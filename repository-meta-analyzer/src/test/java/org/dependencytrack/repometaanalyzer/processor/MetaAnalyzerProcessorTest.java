/*
 * This file is part of Dependency-Track.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) OWASP Foundation. All Rights Reserved.
 */
package org.dependencytrack.repometaanalyzer.processor;

import com.github.packageurl.MalformedPackageURLException;
import com.github.packageurl.PackageURL;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Body;
import com.github.tomakehurst.wiremock.http.ContentTypeHeader;
import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheName;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpStatus;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.errors.StreamsException;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.test.TestRecord;
import org.dependencytrack.common.SecretDecryptor;
import org.dependencytrack.persistence.model.RepositoryType;
import org.dependencytrack.persistence.repository.RepoEntityRepository;
import org.dependencytrack.proto.KafkaProtobufSerde;
import org.dependencytrack.proto.repometaanalysis.v1.AnalysisCommand;
import org.dependencytrack.proto.repometaanalysis.v1.AnalysisResult;
import org.dependencytrack.proto.repometaanalysis.v1.Component;
import org.dependencytrack.proto.repometaanalysis.v1.FetchMeta;
import org.dependencytrack.proto.repometaanalysis.v1.HealthMeta;
import org.dependencytrack.repometaanalyzer.model.ComponentHealthMetaModel;
import org.dependencytrack.repometaanalyzer.model.ScoreCardCheck;
import org.dependencytrack.repometaanalyzer.repositories.RepositoryAnalyzerFactory;
import org.dependencytrack.repometaanalyzer.repositories.health.HealthAnalyzerFactory;
import org.dependencytrack.repometaanalyzer.repositories.health.IHealthMetaAnalyzer;
import org.dependencytrack.repometaanalyzer.serde.KafkaPurlSerde;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.head;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestProfile(MetaAnalyzerProcessorTest.TestProfile.class)
class MetaAnalyzerProcessorTest {

    public static class TestProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.kafka.snappy.enabled", "false"
            );
        }
    }

    private static WireMockServer wireMockServer1;
    private static WireMockServer wireMockServer2;
    private static final String TEST_PURL_JACKSON_BIND = "pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.4";

    private TopologyTestDriver testDriver;
    private TestInputTopic<PackageURL, AnalysisCommand> inputTopic;
    private TestOutputTopic<PackageURL, AnalysisResult> outputTopic;
    @Inject
    RepoEntityRepository repoEntityRepository;

    @Inject
    RepositoryAnalyzerFactory analyzerFactory;

    @InjectMock
    HealthAnalyzerFactory healthAnalyzerFactory;

    @Inject
    EntityManager entityManager;

    @Inject
    @CacheName("metaAnalyzer")
    Cache cache;

    @Inject
    SecretDecryptor secretDecryptor;

    @BeforeEach
    void beforeEach() {
        final var processorSupplier = new MetaAnalyzerProcessorSupplier(
                repoEntityRepository, analyzerFactory, secretDecryptor, cache, healthAnalyzerFactory
        );

        final var valueSerde = new KafkaProtobufSerde<>(AnalysisCommand.parser());
        final var purlSerde = new KafkaPurlSerde();
        final var valueSerdeResult = new KafkaProtobufSerde<>(AnalysisResult.parser());

        final var streamsBuilder = new StreamsBuilder();
        streamsBuilder
                .stream("input-topic", Consumed.with(purlSerde, valueSerde))
                .processValues(processorSupplier)
                .to("output-topic", Produced.with(purlSerde, valueSerdeResult));

        testDriver = new TopologyTestDriver(streamsBuilder.build());
        inputTopic = testDriver.createInputTopic("input-topic", purlSerde.serializer(), valueSerde.serializer());
        outputTopic = testDriver.createOutputTopic("output-topic", purlSerde.deserializer(), valueSerdeResult.deserializer());

        wireMockServer1 = new WireMockServer(1080);
        wireMockServer1.start();
        wireMockServer2 = new WireMockServer(2080);
        wireMockServer2.start();
    }

    @AfterEach
    void afterEach() {
        wireMockServer1.stop();
        wireMockServer1.resetAll();
        wireMockServer2.stop();
        wireMockServer2.resetAll();
        testDriver.close();
        cache.invalidateAll().await().indefinitely();
    }


    @Test
    void testWithNoSupportedRepositoryTypes() throws Exception {
        final TestRecord<PackageURL, AnalysisCommand> inputRecord = new TestRecord<>(new PackageURL(TEST_PURL_JACKSON_BIND), AnalysisCommand.newBuilder().setComponent(Component.newBuilder()
                .setPurl(TEST_PURL_JACKSON_BIND)).build());
        inputTopic.pipeInput(inputRecord);
        assertThat(outputTopic.getQueueSize()).isEqualTo(1);
        assertThat(outputTopic.readRecordsToList()).satisfiesExactly(
                record -> {
                    assertThat(record.key().getType()).isEqualTo(RepositoryType.MAVEN.toString().toLowerCase());
                });
    }

    @Test
    void testMalformedPurl() throws Exception {
        final TestRecord<PackageURL, AnalysisCommand> inputRecord = new TestRecord<>(new PackageURL(TEST_PURL_JACKSON_BIND), AnalysisCommand.newBuilder().setComponent(Component.newBuilder()
                .setPurl("invalid purl")).build());
        Assertions.assertThrows(StreamsException.class, () -> {
            inputTopic.pipeInput(inputRecord);
        }, "no exception thrown");

    }

    @Test
    void testNoAnalyzerApplicable() throws Exception {
        final TestRecord<PackageURL, AnalysisCommand> inputRecord = new TestRecord<>(new PackageURL("pkg:test/com.fasterxml.jackson.core/jackson-databind@2.13.4"), AnalysisCommand.newBuilder().setComponent(Component.newBuilder()
                .setPurl("pkg:test/com.fasterxml.jackson.core/jackson-databind@2.13.4")).build());
        inputTopic.pipeInput(inputRecord);
        assertThat(outputTopic.getQueueSize()).isEqualTo(1);
        assertThat(outputTopic.readRecordsToList()).satisfiesExactly(
                record -> {
                    assertThat(record.key().getType()).isEqualTo("test");
                });

    }

    @Test
    @TestTransaction
    void testInternalRepositoryExternalComponent() throws MalformedPackageURLException {
        entityManager.createNativeQuery("""
                INSERT INTO "REPOSITORY" ("TYPE", "ENABLED","IDENTIFIER", "INTERNAL", "URL", "AUTHENTICATIONREQUIRED", "RESOLUTION_ORDER") VALUES
                                    ('MAVEN',true, 'central', true, 'test.com', false,1);
                """).executeUpdate();

        final TestRecord<PackageURL, AnalysisCommand> inputRecord = new TestRecord<>(new PackageURL("pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.4"), AnalysisCommand.newBuilder().setComponent(Component.newBuilder()
                .setPurl("pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.4").setInternal(false)).build());
        inputTopic.pipeInput(inputRecord);
        assertThat(outputTopic.getQueueSize()).isEqualTo(1);
        assertThat(outputTopic.readRecordsToList()).satisfiesExactly(
                record -> {
                    assertThat(record.key().getType()).isEqualTo(RepositoryType.MAVEN.toString().toLowerCase());
                });

    }

    @Test
    @TestTransaction
    void testExternalRepositoryInternalComponent() throws MalformedPackageURLException {
        entityManager.createNativeQuery("""
                INSERT INTO "REPOSITORY" ("TYPE", "ENABLED","IDENTIFIER", "INTERNAL", "URL", "AUTHENTICATIONREQUIRED", "RESOLUTION_ORDER") VALUES
                                    ('MAVEN',true, 'central', false, 'test.com', false,1);
                """).executeUpdate();

        final TestRecord<PackageURL, AnalysisCommand> inputRecord = new TestRecord<>(new PackageURL("pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.4"), AnalysisCommand.newBuilder().setComponent(Component.newBuilder()
                .setPurl("pkg:maven/com.fasterxml.jackson.core/jackson-databind@2.13.4").setInternal(true)).build());
        inputTopic.pipeInput(inputRecord);
        assertThat(outputTopic.getQueueSize()).isEqualTo(1);
        assertThat(outputTopic.readRecordsToList()).satisfiesExactly(
                record -> {
                    assertThat(record.key().getType()).isEqualTo(RepositoryType.MAVEN.toString().toLowerCase());
                });

    }

    @Test
    @TestTransaction
    void testRepoMetaWithIntegrityMetaWithAuth() throws Exception {
        entityManager.createNativeQuery("""
                        INSERT INTO "REPOSITORY" ("TYPE", "ENABLED","IDENTIFIER", "INTERNAL", "URL", "AUTHENTICATIONREQUIRED", "RESOLUTION_ORDER", "USERNAME", "PASSWORD") VALUES
                                            ('NPM', true, 'central', true, :url, true, 1, 'username', :encryptedPassword);
                        """)
                .setParameter("encryptedPassword", secretDecryptor.encryptAsString("password"))
                .setParameter("url", String.format("http://localhost:%d", wireMockServer1.port()))
                .executeUpdate();
        wireMockServer1.stubFor(get(urlPathEqualTo("/-/package/%40apollo%2Ffederation/dist-tags"))
                .willReturn(aResponse().withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withResponseBody(Body.ofBinaryOrText("""
                                        {
                                            "latest": "v6.6.6"
                                        }
                                        """.getBytes(),
                                new ContentTypeHeader("application/json"))).withStatus(HttpStatus.SC_OK)));

        wireMockServer1.stubFor(head(urlPathEqualTo("/@apollo/federation/-/@apollo/federation-0.19.1.tgz"))
                .willReturn(aResponse().withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withResponseBody(Body.ofBinaryOrText("".getBytes(),
                                new ContentTypeHeader("application/json")))
                        .withHeader("X-Checksum-MD5", "md5hash").withStatus(HttpStatus.SC_OK)));

        UUID uuid = UUID.randomUUID();
        final TestRecord<PackageURL, AnalysisCommand> inputRecord = new TestRecord<>(new PackageURL("pkg:npm/@apollo/federation@0.19.1"),
                AnalysisCommand.newBuilder()
                        .setComponent(Component.newBuilder()
                                .setPurl("pkg:npm/@apollo/federation@0.19.1")
                                .setUuid(uuid.toString())
                                .setInternal(true))
                        .setFetchMeta(FetchMeta.FETCH_META_INTEGRITY_DATA_AND_LATEST_VERSION).build());

        inputTopic.pipeInput(inputRecord);
        assertThat(outputTopic.getQueueSize()).isEqualTo(1);
        assertThat(outputTopic.readRecordsToList()).satisfiesExactly(
                record -> {
                    assertThat(record.key().getType()).isEqualTo(RepositoryType.NPM.toString().toLowerCase());
                    assertThat(record.value()).isNotNull();
                    final AnalysisResult result = record.value();
                    assertThat(result.hasComponent()).isTrue();
                    assertThat(result.getComponent().getUuid()).isEqualTo(uuid.toString());
                    assertThat(result.getRepository()).isEqualTo("central");
                    assertThat(result.getLatestVersion()).isEqualTo("v6.6.6");
                    assertThat(result.hasPublished()).isFalse();
                    assertThat(result.hasIntegrityMeta()).isTrue();
                    final var integrityMeta = result.getIntegrityMeta();
                    assertThat(integrityMeta.getMd5()).isEqualTo("md5hash");
                    assertThat(integrityMeta.getMetaSourceUrl()).contains("/@apollo/federation/-/@apollo/federation-0.19.1.tgz");
                });

    }

    @Test
    @TestTransaction
    void testDifferentSourcesForRepoMeta() throws Exception {
        entityManager.createNativeQuery("""
                        INSERT INTO "REPOSITORY" ("TYPE", "ENABLED","IDENTIFIER", "INTERNAL", "URL", "AUTHENTICATIONREQUIRED", "RESOLUTION_ORDER") VALUES
                                            ('NPM', true, 'central', true, :url1, false, 1),
                                            ('NPM', true, 'internal', true, :url2, false, 2);
                        """)
                .setParameter("url1", String.format("http://localhost:%d", wireMockServer1.port()))
                .setParameter("url2", String.format("http://localhost:%d", wireMockServer2.port()))
                .executeUpdate();
        wireMockServer1.stubFor(get(urlPathEqualTo("/-/package/%40apollo%2Ffederation/dist-tags"))
                .willReturn(aResponse().withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withResponseBody(Body.ofBinaryOrText("""
                                        {
                                            "type": "version"
                                        }
                                        """.getBytes(),
                                new ContentTypeHeader("application/json")))
                        .withStatus(HttpStatus.SC_OK)));

        wireMockServer2.stubFor(get(urlPathEqualTo("/-/package/%40apollo%2Ffederation/dist-tags"))
                .willReturn(aResponse().withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withResponseBody(Body.ofBinaryOrText("""
                                        {
                                            "latest": "v6.6.6"
                                        }
                                        """.getBytes(),
                                new ContentTypeHeader("application/json")))
                        .withStatus(HttpStatus.SC_OK)));
        UUID uuid = UUID.randomUUID();
        final TestRecord<PackageURL, AnalysisCommand> inputRecord = new TestRecord<>(new PackageURL("pkg:npm/@apollo/federation@0.19.1"),
                AnalysisCommand.newBuilder()
                        .setComponent(Component.newBuilder()
                                .setPurl("pkg:npm/@apollo/federation@0.19.1")
                                .setUuid(uuid.toString())
                                .setInternal(true))
                        .setFetchMeta(FetchMeta.FETCH_META_LATEST_VERSION).build());

        inputTopic.pipeInput(inputRecord);
        assertThat(outputTopic.getQueueSize()).isEqualTo(1);
        assertThat(outputTopic.readRecordsToList()).satisfiesExactly(
                record -> {
                    assertThat(record.key().getType()).isEqualTo(RepositoryType.NPM.toString().toLowerCase());
                    assertThat(record.value()).isNotNull();
                    final AnalysisResult result = record.value();
                    assertThat(result.hasComponent()).isTrue();
                    assertThat(result.getComponent().getUuid()).isEqualTo(uuid.toString());
                    assertThat(result.getRepository()).isEqualTo("internal");
                    assertThat(result.getLatestVersion()).isEqualTo("v6.6.6");
                    assertThat(result.hasPublished()).isFalse();
                });

    }

    @Test
    @TestTransaction
    void testDifferentSourcesForRepoAndIntegrityMeta() throws Exception {
        entityManager.createNativeQuery("""
                        INSERT INTO "REPOSITORY" ("TYPE", "ENABLED","IDENTIFIER", "INTERNAL", "URL", "AUTHENTICATIONREQUIRED", "RESOLUTION_ORDER") VALUES
                                            ('NPM', true, 'central', true, :url1, false, 1),
                                            ('NPM', true, 'internal', true, :url2, false, 2);
                        """)
                .setParameter("url1", String.format("http://localhost:%d", wireMockServer1.port()))
                .setParameter("url2", String.format("http://localhost:%d", wireMockServer2.port()))
                .executeUpdate();
        wireMockServer1.stubFor(get(urlPathEqualTo("/-/package/%40apollo%2Ffederation/dist-tags"))
                .willReturn(aResponse().withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withResponseBody(Body.ofBinaryOrText("""
                                        {
                                        }
                                        """.getBytes(),
                                new ContentTypeHeader("application/json")))
                        .withStatus(HttpStatus.SC_OK)));

        wireMockServer1.stubFor(head(urlPathEqualTo("/@apollo/federation/-/@apollo/federation-0.19.1.tgz"))
                .willReturn(aResponse().withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withResponseBody(Body.ofBinaryOrText("".getBytes(),
                                new ContentTypeHeader("application/json"))).withHeader("X-Checksum-MD5", "md5hash")
                        .withStatus(HttpStatus.SC_OK)));
        wireMockServer2.stubFor(get(urlPathEqualTo("/-/package/%40apollo%2Ffederation/dist-tags"))
                .willReturn(aResponse().withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withResponseBody(Body.ofBinaryOrText("""
                                        {
                                            "latest": "v6.6.6"
                                        }
                                        """.getBytes(),
                                new ContentTypeHeader("application/json")))
                        .withStatus(HttpStatus.SC_OK)));
        UUID uuid = UUID.randomUUID();
        final TestRecord<PackageURL, AnalysisCommand> inputRecord = new TestRecord<>(new PackageURL("pkg:npm/@apollo/federation@0.19.1"),
                AnalysisCommand.newBuilder()
                        .setComponent(Component.newBuilder()
                                .setPurl("pkg:npm/@apollo/federation@0.19.1")
                                .setUuid(uuid.toString())
                                .setInternal(true))
                        .setFetchMeta(FetchMeta.FETCH_META_INTEGRITY_DATA_AND_LATEST_VERSION).build());

        inputTopic.pipeInput(inputRecord);
        assertThat(outputTopic.getQueueSize()).isEqualTo(1);
        assertThat(outputTopic.readRecordsToList()).satisfiesExactly(
                record -> {
                    assertThat(record.key().getType()).isEqualTo(RepositoryType.NPM.toString().toLowerCase());
                    assertThat(record.value()).isNotNull();
                    final AnalysisResult result = record.value();
                    assertThat(result.hasComponent()).isTrue();
                    assertThat(result.getComponent().getUuid()).isEqualTo(uuid.toString());
                    assertThat(result.getRepository()).isEqualTo("internal");
                    assertThat(result.getLatestVersion()).isEqualTo("v6.6.6");
                    assertThat(result.hasPublished()).isFalse();
                    assertThat(result.hasIntegrityMeta()).isTrue();
                    final var integrityMeta = result.getIntegrityMeta();
                    assertThat(integrityMeta.getMd5()).isEqualTo("md5hash");
                    assertThat(integrityMeta.getMetaSourceUrl()).isEqualTo("http://localhost:1080/@apollo/federation/-/@apollo/federation-0.19.1.tgz");
                });
    }

    @Test
    void testHealthFetchMetaNoAnalyzer() throws Exception {
        // Return two fake analyzers from HealthAnalyzerFactory whose results should be merged
        when(healthAnalyzerFactory.createApplicableAnalyzers(any(PackageURL.class)))
                .thenReturn(Collections.emptyList());

        // Build AnalysisCommand
        AnalysisCommand command = AnalysisCommand.newBuilder()
                .setComponent(
                        Component.newBuilder()
                                .setPurl(TEST_PURL_JACKSON_BIND)
                                .build()
                )
                .setFetchMeta(FetchMeta.FETCH_META_HEALTH)
                .build();


        // Pipe into the input topic
        inputTopic.pipeInput(new TestRecord<>(new PackageURL(TEST_PURL_JACKSON_BIND), command));

        // Assertions on output
        assertThat(outputTopic.getQueueSize()).isEqualTo(1);
        AnalysisResult result = outputTopic.readKeyValuesToList().getFirst().value;

        HealthMeta expected = HealthMeta
                .newBuilder()
                .build();
        assertThat(result.getHealthMeta()).isEqualTo(expected);
    }

    @Test
    void testHealthFetchMetaAllFields() throws Exception {
        // Testing Constants
        final String TEST_TIMESTAMP = "2025-06-19T00:00:00Z";
        final int EXPECTED_STARS = 42;
        final int EXPECTED_FORKS = 17;
        final int EXPECTED_CONTRIBUTORS = 5;
        final float EXPECTED_COMMIT_FREQUENCY = 3.7f;
        final int EXPECTED_OPEN_ISSUES = 13;
        final int EXPECTED_OPEN_PRS = 4;
        final Instant EXPECTED_LAST_COMMIT_INSTANT = Instant.parse(TEST_TIMESTAMP);
        final Timestamp EXPECTED_LAST_COMMIT_PROTO_TS = Timestamps.parse(TEST_TIMESTAMP);
        final int EXPECTED_BUS_FACTOR = 2;
        final boolean EXPECTED_HAS_README = true;
        final boolean EXPECTED_HAS_COC = false;
        final boolean EXPECTED_HAS_SECURITY_POLICY = true;
        final int EXPECTED_DEPENDENTS = 123;
        final int EXPECTED_FILES = 256;
        final boolean EXPECTED_IS_REPO_ARCHIVED = false;
        final String CHECK_NAME = "ci-checked";  // dummy value
        final String CHECK_DESCRIPTION = "CI runs on each PR";  // dummy value
        final float CHECK_SCORE = 0.9f;
        final String CHECK_REASON = "All builds green";  // dummy value
        final List<String> CHECK_DETAILS = Arrays.asList("build-1", "build-2");
        final String CHECK_DOC_URL = "https://ossf.io/scorecard/docs/checks#ci-checked";  // dummy URL

        final float EXPECTED_SCORECARD_SCORE = 8.5f;
        final String EXPECTED_SCORECARD_VERSION = "v4.2.0";
        final Instant EXPECTED_SCORECARD_INSTANT = Instant.parse(TEST_TIMESTAMP);
        final Timestamp EXPECTED_SCORECARD_PROTO_TS = Timestamps.parse(TEST_TIMESTAMP);

        // Mock our factory with a single analyzer that returns all of the above
        IHealthMetaAnalyzer fakeAnalyzer = mock(IHealthMetaAnalyzer.class);
        when(healthAnalyzerFactory.createApplicableAnalyzers(any(PackageURL.class)))
                .thenReturn(Collections.singletonList(fakeAnalyzer));

        // Component setup
        org.dependencytrack.persistence.model.Component dummyComponent =
                new org.dependencytrack.persistence.model.Component();
        dummyComponent.setPurl(TEST_PURL_JACKSON_BIND);

        // Fully populated model
        ComponentHealthMetaModel healthMetaModel = new ComponentHealthMetaModel(dummyComponent);
        healthMetaModel.setStars(EXPECTED_STARS);
        healthMetaModel.setForks(EXPECTED_FORKS);
        healthMetaModel.setContributors(EXPECTED_CONTRIBUTORS);
        healthMetaModel.setCommitFrequency(EXPECTED_COMMIT_FREQUENCY);
        healthMetaModel.setOpenIssues(EXPECTED_OPEN_ISSUES);
        healthMetaModel.setOpenPRs(EXPECTED_OPEN_PRS);
        healthMetaModel.setLastCommitDate(EXPECTED_LAST_COMMIT_INSTANT);
        healthMetaModel.setBusFactor(EXPECTED_BUS_FACTOR);
        healthMetaModel.setHasReadme(EXPECTED_HAS_README);
        healthMetaModel.setHasCodeOfConduct(EXPECTED_HAS_COC);
        healthMetaModel.setHasSecurityPolicy(EXPECTED_HAS_SECURITY_POLICY);
        healthMetaModel.setDependents(EXPECTED_DEPENDENTS);
        healthMetaModel.setFiles(EXPECTED_FILES);
        healthMetaModel.setIsRepoArchived(EXPECTED_IS_REPO_ARCHIVED);

        ScoreCardCheck modelCheck = new ScoreCardCheck();
        modelCheck.setName(CHECK_NAME);
        modelCheck.setDescription(CHECK_DESCRIPTION);
        modelCheck.setScore(CHECK_SCORE);
        modelCheck.setReason(CHECK_REASON);
        modelCheck.setDetails(CHECK_DETAILS);
        modelCheck.setDocumentationUrl(CHECK_DOC_URL);
        healthMetaModel.setScoreCardChecks(Collections.singletonList(modelCheck));

        healthMetaModel.setScoreCardScore(EXPECTED_SCORECARD_SCORE);
        healthMetaModel.setScoreCardReferenceVersion(EXPECTED_SCORECARD_VERSION);
        healthMetaModel.setScoreCardTimestamp(EXPECTED_SCORECARD_INSTANT);

        when(fakeAnalyzer.analyze(any(PackageURL.class)))
                .thenReturn(healthMetaModel);

        // Send into input topic
        AnalysisCommand command = AnalysisCommand.newBuilder()
                .setComponent(Component.newBuilder()
                        .setPurl(TEST_PURL_JACKSON_BIND)
                        .build())
                .setFetchMeta(FetchMeta.FETCH_META_HEALTH)
                .build();

        inputTopic.pipeInput(new TestRecord<>(new PackageURL(TEST_PURL_JACKSON_BIND), command));

        // Retrieve result
        assertThat(outputTopic.getQueueSize()).isEqualTo(1);
        AnalysisResult result = outputTopic.readKeyValuesToList().getFirst().value;
        HealthMeta actual = result.getHealthMeta();

        // --- Build expected proto ---
        HealthMeta expected = HealthMeta.newBuilder()
                .setStars(EXPECTED_STARS)
                .setForks(EXPECTED_FORKS)
                .setContributors(EXPECTED_CONTRIBUTORS)
                .setCommitFrequency(EXPECTED_COMMIT_FREQUENCY)
                .setOpenIssues(EXPECTED_OPEN_ISSUES)
                .setOpenPRs(EXPECTED_OPEN_PRS)
                .setLastCommitDate(EXPECTED_LAST_COMMIT_PROTO_TS)
                .setBusFactor(EXPECTED_BUS_FACTOR)
                .setHasReadme(EXPECTED_HAS_README)
                .setHasCodeOfConduct(EXPECTED_HAS_COC)
                .setHasSecurityPolicy(EXPECTED_HAS_SECURITY_POLICY)
                .setDependents(EXPECTED_DEPENDENTS)
                .setFiles(EXPECTED_FILES)
                .setIsRepoArchived(EXPECTED_IS_REPO_ARCHIVED)
                .addScoreCardChecks(
                        org.dependencytrack.proto.repometaanalysis.v1.ScoreCardCheck.newBuilder()
                                .setName(CHECK_NAME)
                                .setDescription(CHECK_DESCRIPTION)
                                .setScore(CHECK_SCORE)
                                .setReason(CHECK_REASON)
                                .addAllDetails(CHECK_DETAILS)
                                .setDocumentationUrl(CHECK_DOC_URL)
                )
                .setScoreCardScore(EXPECTED_SCORECARD_SCORE)
                .setScoreCardReferenceVersion(EXPECTED_SCORECARD_VERSION)
                .setScoreCardTimestamp(EXPECTED_SCORECARD_PROTO_TS)
                .build();

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void testHealthFetchMetaWithMerge() throws Exception {
        // Return two fake analyzers from HealthAnalyzerFactory whose results should be merged
        IHealthMetaAnalyzer fakeAnalyzer1 = mock(IHealthMetaAnalyzer.class);
        IHealthMetaAnalyzer fakeAnalyzer2 = mock(IHealthMetaAnalyzer.class);
        when(healthAnalyzerFactory.createApplicableAnalyzers(any(PackageURL.class)))
                .thenReturn(List.of(fakeAnalyzer1, fakeAnalyzer2));

        // Setup dummy values
        org.dependencytrack.persistence.model.Component dummyComponent
                = new org.dependencytrack.persistence.model.Component();
        dummyComponent.setPurl(TEST_PURL_JACKSON_BIND);

        final int TEST_STARS = 42;
        final int TEST_BUS_FACTOR = 9000;
        final int TEST_CONTRIBUTORS = 9001;
        final float TEST_SCORECARD_SCORE = 10.0f;

        ComponentHealthMetaModel healthMetaModel1 = new ComponentHealthMetaModel(dummyComponent);
        healthMetaModel1.setStars(TEST_STARS);
        healthMetaModel1.setBusFactor(TEST_BUS_FACTOR);
        healthMetaModel1.setContributors(TEST_CONTRIBUTORS);
        ComponentHealthMetaModel healthMetaModel2 = new ComponentHealthMetaModel(dummyComponent);
        healthMetaModel2.setScoreCardScore(TEST_SCORECARD_SCORE);

        when(fakeAnalyzer1.analyze(any(PackageURL.class))).thenReturn(healthMetaModel1);
        when(fakeAnalyzer2.analyze(any(PackageURL.class))).thenReturn(healthMetaModel2);

        // Build AnalysisCommand
        AnalysisCommand command = AnalysisCommand.newBuilder()
                .setComponent(
                        Component.newBuilder()
                                .setPurl(TEST_PURL_JACKSON_BIND)
                                .build()
                )
                .setFetchMeta(FetchMeta.FETCH_META_HEALTH)
                .build();


        // Pipe into the input topic
        inputTopic.pipeInput(new TestRecord<>(new PackageURL(TEST_PURL_JACKSON_BIND), command));

        // Assertions on output
        assertThat(outputTopic.getQueueSize()).isEqualTo(1);
        AnalysisResult result = outputTopic.readKeyValuesToList().getFirst().value;

        HealthMeta expected = HealthMeta
                .newBuilder()
                .setStars(TEST_STARS)
                .setBusFactor(TEST_BUS_FACTOR)
                .setContributors(TEST_CONTRIBUTORS)
                .setScoreCardScore(TEST_SCORECARD_SCORE)
                .build();
        assertThat(result.getHealthMeta()).isEqualTo(expected);
    }
}