# 로컬 테스트 시드 데이터

`mock_test_data.sql` 파일은 아직 의도적으로 커밋하지 않았습니다.

`local-test` 프로파일은 다음 경로에서 파일을 찾습니다.

```text
src/test/resources/db/seed/mock_test_data.sql
```

CI/CD가 주기적으로 생성된 mock data dump를 제공하기 시작하면, 해당 파일을 위 경로에 배치하세요.
seed runner는 Flyway가 스키마를 생성한 뒤 이 파일을 실행하고, 실행한 스크립트 위치와 SHA-256 checksum을
`local_test_seed_history`에 기록합니다. 따라서 동일한 로컬 데이터베이스에는 같은 내용의 시드 파일이 반복 적용되지 않습니다.
같은 경로의 파일이 새 mock data dump로 갱신되면 checksum이 달라지므로, 갱신된 시드 파일은 다시 적용됩니다.
