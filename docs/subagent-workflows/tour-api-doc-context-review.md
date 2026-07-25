# Tour API Batch Doc/Context Review Subagent Workflow

## Goal

Use this workflow before committing Tour API batch changes when the risk is not only code correctness, but drift between:

- external Tour API reference documents under `docs/`
- internal architecture docs such as `docs/adr/0001_adr_etl_pipeline.md`
- the staged or working-tree implementation
- the commit/PR summary that will describe the change

The parent agent owns orchestration and final judgment. Subagents return evidence and findings only.

## Scope Gate

Before spawning subagents, the parent agent must decide and state one review scope:

- `staged-only`: use `git diff --cached --name-status` and `git diff --cached`
- `working-tree`: use `git status --short`, `git diff --cached`, and `git diff`

For the current Tour API batch refactor, prefer `working-tree` unless the user explicitly wants staged-only review, because the batch client, readers, processors, scheduler, and several tests may still be unstaged or untracked.

## Quick Run Guide

현재 변경셋처럼 staged/unstaged/untracked가 섞인 상태에서는 아래 순서로 실행한다.

1. Parent agent가 `git status --short`, `git diff --cached --name-status`, `git diff --name-status`로 검토 범위를 확정한다.
2. `Document Inventory Agent`와 `Implementation Map Agent`를 병렬 실행한다.
3. 두 결과가 모두 나오면 `External API Context Mismatch Agent`, `Architecture/Workflow Drift Agent`, `Persistence Semantics Mismatch Agent`를 병렬 실행한다.
4. 마지막에 `Synthesis Agent`가 중복을 병합하고 `ready`, `ready_with_doc_changes`, `needs_code_fix`, `needs_scope_cleanup` 중 하나로 결론을 낸다.

현재 상태의 기본 판단은 `working-tree` 검토다. staged-only로 돌리면 실제 batch reader/client/processor/scheduler 구현 일부가 검토에서 빠질 수 있다.

Use these role mappings when spawning Codex subagents:

```text
Document Inventory Agent -> documentation-engineer 또는 docs-researcher
Implementation Map Agent -> code-mapper 또는 spring-boot-engineer
External API Context Mismatch Agent -> docs-researcher
Architecture/Workflow Drift Agent -> architect-reviewer 또는 documentation-engineer
Persistence Semantics Mismatch Agent -> sql-pro 또는 spring-boot-engineer
Synthesis Agent -> knowledge-synthesizer
```

## Agent Roles

### 1. Document Inventory Agent

Recommended role: `documentation-engineer` or `docs-researcher`

Purpose:

- identify which documents are authoritative for the batch/API behavior
- extract only claims that can be checked against code
- separate external API facts from internal design decisions

Inputs:

- `docs/adr/0001_adr_etl_pipeline.md`
- all new or changed docs under `docs/`
- file names of `.docx` specs, if present
- review scope file list from the parent agent

Output contract:

```text
Document Facts
- fact_id:
- source_path:
- source_section_or_location:
- claim:
- category: external_api | internal_architecture | operator_workflow | test_expectation
- confidence: high | medium | low
- code_paths_expected_to_reflect_it:
- ambiguity:
```

Prompt template:

```text
You are the Document Inventory Agent for a Spring Boot Tour API batch refactor.

Review the documents only. Do not review implementation correctness yet.

Scope:
- docs/adr/0001_adr_etl_pipeline.md
- changed or newly added docs under docs/
- current review mode: <staged-only|working-tree>

Extract checkable facts about:
- Tour API base/list endpoint behavior
- Tour API detail endpoint behavior
- required identifiers, pagination, response nullability, and partial data behavior
- intended ETL/batch architecture and scheduling
- persistence/update semantics
- manual E2E or operator workflow expectations

Return facts in the requested output contract. Mark any `.docx` fact as low confidence unless you were able to extract readable text from the file.
```

### 2. Implementation Map Agent

Recommended role: `code-mapper` or `spring-boot-engineer`

Purpose:

- map actual changed code paths and execution flow
- avoid judging docs yet
- produce a concise implementation model that mismatch agents can compare against document facts

Inputs:

- selected review diff
- changed files under `src/main/java`
- changed files under `src/test/java`
- changed config files such as `build.gradle` and `application-*.yaml`

Output contract:

```text
Implementation Claims
- impl_id:
- code_path:
- behavior:
- entrypoint_or_step:
- upstream_dependency:
- downstream_effect:
- test_coverage:
- evidence:
```

Prompt template:

```text
You are the Implementation Map Agent for a Spring Boot Tour API batch refactor.

Review the selected diff and changed files. Do not compare against docs yet.

Map the real implementation behavior for:
- batch job and step structure
- base/list item reading
- detail item reading
- Tour API client calls and parameter construction
- processors and null/empty handling
- PlaceItemWriter upsert and partial update semantics
- scheduler configuration
- manual and unit test coverage

Return implementation claims using the requested output contract. Include exact file paths and line references where possible.
```

### 3. External API Context Mismatch Agent

Recommended role: `docs-researcher`

Purpose:

- compare external Tour API document facts with actual API client/reader behavior
- focus on wrong endpoint assumptions, parameter drift, pagination, and response-shape mistakes

Inputs:

- Document Inventory Agent output where `category = external_api`
- Implementation Map Agent output for client/readers/processors/tests
- changed Tour API client/reader/processor files

Output contract:

```text
External API Mismatch Findings
- severity: blocker | high | medium | low
- fact_id:
- impl_id:
- title:
- mismatch:
- evidence:
- impact:
- recommended_fix_or_verification:
```

Prompt template:

```text
You are the External API Context Mismatch Agent.

Compare documented Tour API behavior against the implementation map. Only report mismatches where there is concrete evidence or a high-risk ambiguity.

Check:
- endpoint separation between base/list data and detail data
- required identifiers for detail fetches
- pagination and total-count handling
- MobileOS/MobileApp/serviceKey defaults
- null, blank, and missing field behavior
- response array/object assumptions
- API rate, stagger, or scheduling assumptions if documented

Return findings in severity order. Do not propose broad refactors; propose the smallest verification or fix.
```

### 4. Architecture/Workflow Drift Agent

Recommended role: `architect-reviewer` or `documentation-engineer`

Purpose:

- compare internal ADR/operator docs with the implemented batch structure
- catch cases where docs describe one-step sync, on-demand behavior, or different operational semantics

Inputs:

- Document Inventory Agent output where `category = internal_architecture` or `operator_workflow`
- Implementation Map Agent output for job/steps/scheduler/tests
- changed ADR and batch config files

Output contract:

```text
Architecture Drift Findings
- severity: blocker | high | medium | low
- source_doc:
- code_path:
- title:
- documented_behavior:
- implemented_behavior:
- user_or_operator_impact:
- recommended_doc_or_code_change:
```

Prompt template:

```text
You are the Architecture/Workflow Drift Agent.

Compare internal docs and operator expectations against the implemented Tour API batch workflow.

Check for drift around:
- on-demand sync versus staggered scheduled batch
- one-step sync versus base/detail split
- job/step names and execution order
- restartability and failure boundaries
- test names and manual E2E instructions
- scheduler enablement and environment assumptions
- what is safe to claim in a commit message or PR description

Return only actionable drift findings. For each item, say whether the fix should be docs, code, tests, or commit wording.
```

### 5. Persistence Semantics Mismatch Agent

Recommended role: `sql-pro` or `spring-boot-engineer`

Purpose:

- verify that documented partial-update expectations match `PlaceItemWriter`
- catch data-loss or stale-data semantics caused by `COALESCE`, blank strings, or conflict keys

Inputs:

- Document Inventory Agent output for persistence/update facts
- Implementation Map Agent output for writer behavior
- `src/main/java/kr/bi/go_to/batch/writer/PlaceItemWriter.java`
- relevant tests

Output contract:

```text
Persistence Semantics Findings
- severity: blocker | high | medium | low
- title:
- documented_or_claimed_behavior:
- implemented_sql_behavior:
- edge_case:
- impact:
- recommended_fix_or_test:
```

Prompt template:

```text
You are the Persistence Semantics Mismatch Agent.

Focus only on DB write semantics and tests.

Compare the claimed partial update behavior with the actual SQL and writer control flow:
- ON CONFLICT target
- COALESCE behavior for null values
- behavior for empty strings versus nulls
- whether existing values are preserved as claimed
- whether incoming fresh values can overwrite stale values
- transactional and batch-write boundaries
- tests that prove or fail to prove the behavior

Return findings with exact SQL/control-flow evidence.
```

### 6. Synthesis Agent

Recommended role: `knowledge-synthesizer`

Purpose:

- merge duplicate findings
- distinguish actual mismatch from uncertain document gaps
- produce a commit-readiness decision

Inputs:

- all mismatch agent outputs
- parent agent's selected review scope

Output contract:

```text
Synthesis
- decision: ready | ready_with_doc_changes | needs_code_fix | needs_scope_cleanup
- top_findings:
- doc_updates_needed:
- code_or_test_updates_needed:
- commit_message_warnings:
- suggested_review_order:
```

Prompt template:

```text
You are the Synthesis Agent.

Merge the subagent outputs into one commit-readiness assessment.

Rules:
- Deduplicate findings across agents.
- Prefer concrete evidence over speculation.
- If a finding is caused by unstaged files being outside the chosen review scope, classify it as scope cleanup.
- Separate doc-only fixes from code/test fixes.
- Identify claims that should not appear in the commit message unless verified.

Return the synthesis contract only.
```

## Parent Agent Execution Order

1. Run scope commands and save the file list mentally:

```sh
git status --short
git diff --cached --name-status
git diff --name-status
```

2. Spawn Document Inventory Agent and Implementation Map Agent in parallel.

3. Wait for both outputs.

4. Spawn these in parallel using the two outputs:

- External API Context Mismatch Agent
- Architecture/Workflow Drift Agent
- Persistence Semantics Mismatch Agent

5. Wait for all mismatch outputs.

6. Spawn Synthesis Agent.

7. Parent agent reviews the synthesis against the actual diff before reporting to the user.

## Recommended Current Review Scope

For the current change summary, use `working-tree`.

Reason: `git status --short` indicates several Tour API batch implementation files are untracked or unstaged while some docs/tests/build files are staged. A staged-only review would likely miss the actual reader/client/processor/scheduler implementation and produce false confidence.

## Final Report Format

The parent agent should report:

```text
검토 범위: staged-only | working-tree

결론:
- ready | ready_with_doc_changes | needs_code_fix | needs_scope_cleanup

Blockers:
- ...

문서/코드 context 불일치:
- ...

문서만 고치면 되는 항목:
- ...

코드/테스트 수정이 필요한 항목:
- ...

커밋 메시지에서 피해야 할 주장:
- ...
```
