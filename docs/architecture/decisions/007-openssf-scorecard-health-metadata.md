| Status   | Date       | Author(s)                          |
| :------- | :--------- | :--------------------------------- |
| Proposed | 2025-07-30 | [@flodt](https://github.com/flodt) |

## Context

> What is the issue that we're seeing that is motivating this decision or change?

The overarching motivation for this change is the improvement of dependency assessment processes that involve DependencyTrack in practice.

Proper risk assessment is critical to software supply chain security. The final assessment of a particular dependency, even when rooted in a fixed procedural context, is often based on the developer's experience or impression of the project in question.

Furthermore, contextualizing existing vulnerabilities is difficult without a good understanding of the security posture of a given project. In some cases, it can be unclear whether a certain dependency is worthwhile to hold onto, or should rather be replaced with another alternative. Understanding the concrete development activity patterns and maintainer focus can further help to establish trust or sow doubts in a project.

We want to address this by integrating a variety of project health and development activity metadata, as well as OpenSSF Scorecard scores into DependencyTrack.
This data shall be available via the API, in the frontend, and to the CEL policies, enabling the creation of powerful and expressive rules for dependency evaluation (e.g. the combination of maintenance activity indicators with the existence of vulnerabilities; think: will a particular problem be fixed anytime soon?)

Adding support for OpenSSF Scorecard has previously been generally discussed in the issue [#3048](https://github.com/DependencyTrack/dependency-track/issues/3048).

## Decision

> What is the change that we're proposing and/or doing?

We expand the Hyades repository meta analyzer service with the newly introduced "health" metadata, adding structures parallel to the existing meta analyzer subsystem. The mapping of components to health metadata is performed via the PURL coordinates (henceforth also referred to as PURL).
A new `FETCH_META_HEALTH` command triggers the new behavior, where the processor triggers all applicable analyzers for a particular PURL.

The set of analyzers can be freely expanded and currently features an integration for metadata from https://deps.dev as well as the GitHub API.

Evidently, a central step in this process is establishing a mapping between software packages ("components" in DT) and their corresponding source code repositories. Many package registries request this data from the authors and publish the link to the repository with the package. The deps.dev project aggregates these links across multiple package registries and generally supports determining the source code repository for a package with some caveats (lacking data quality, bad formatting, repository URL not directly included but behind a forwarding).

The current integration thus establishes a link between the component and its source code project on GitHub, and pulls the available data - both from deps.dev (number of known dependents, number of open issues, forks, watchers, and the OpenSSF Scorecard) as well as from GitHub (repository size, last commit, contributor statistics, bus factor, issue age, ...).

This new data is persisted in the `HEALTH_META_COMPONENT` table in the database with an API endpoint to retrieve the data under `/v1/component/{uuid}/health`.
The frontend features a new Quality & Metadata tab in the component view to show the data, and the project components table is enriched with the OpenSSF Scorecard score.
The CEL policy engine is given a further variable `health` which makes the new data available to CEL expressions.

In the CEL engine, the purl coordinates are used to map to the associated health metadata, with the added complexity that not every data point is available for every component. Examples could be projects not hosted on GitHub that would be missing development activity insights, or projects for which the OpenSSF Scorecard is not executed.
For this reason, the policy engine now compares the fields populated in the `health` argument with what is listed in that script's requirements, and skips the condition evaluation if a value that would be used is not available. 
For example, if a policy references `health.scoreCardChecks.maintained <= 5.0` in a condition, but the project in question does not have any OpenSSF Scorecard data (as the Scorecard checks were never run on this project), the engine will skip this condition rather than failing it outright.
This behavior could be easily applied in a generalized fashion to the other variables as well; currently missing data leads to a policy failure in any case.

## Consequences

> What becomes easier or more difficult to do because of this change?

From a user-facing perspective, the available information for fine-granular, contextualized and expressive policies is greatly increased.
The availability of the OpenSSF Scorecard as a de-facto standard for the evaluation of the security posture of open-source projects makes it easier to quickly assess the quality of a dependency (and decide on its usage or consider replacement).

The processing time needed for retrieving the external data is substantial (ca. 1 hour for an SBOM the size of DT itself), especially owing to the slowness of the GitHub contributor statistics endpoint (required for commit frequencies and bus factor computation).
In the current configuration, this can lead to the Kafka polling interval overstretching its bounds, the worker thread being terminated and the processing being retried (leading to lost time but no data loss). A longer polling interval might need to be configured (`max.poll.interval.ms`).

Lastly, the data availability is currently limited to projects that are listed on deps.dev (as a single service to provide the mapping between packages and repositories). Bad data quality or missing data can lead to missing values in some cases.
This, combined with the issues discussed above leads to the fact that not all health metadata can be available for all projects.

The availability of data can be improved as future work by integrating more sources on package data, as well as enabling support for Bitbucket or GitLab instances.

