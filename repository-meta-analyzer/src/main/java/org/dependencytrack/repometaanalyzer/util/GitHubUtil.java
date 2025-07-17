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
package org.dependencytrack.repometaanalyzer.util;

import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;

import java.io.IOException;
import java.util.Date;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public final class GitHubUtil {
    private GitHubUtil() {
    }

    public static GitHub connectToGitHub(String user, String password, String githubUrl) throws IOException {
        final GitHub github;
        if (isNotBlank(user) && isNotBlank(password)) {
            github = GitHub.connect(user, password);
        } else if (isBlank(user) && isNotBlank(password)) {
            github = GitHub.connectUsingOAuth(githubUrl, password);
        } else {
            github = GitHub.connectAnonymously();
        }
        return github;
    }

    public static Date getRepositoryCreatedAt(final GHRepository repo) throws IOException {
        return repo.getCreatedAt();
    }
}
