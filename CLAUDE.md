# goto-be 프로젝트 규칙

## 커밋 시 제외 대상

커밋할 때 아래 파일/폴더는 제외한다.
- 마크다운 문서 파일 (`*.md`) — `CLAUDE.md` 포함
- `.claude/` 폴더

## 작업 완료 시

작업을 완료하면 `TODO.md`의 해당 항목을 `- [x]`로 체크한다.

## 코드 작성 후 / 커밋 전

반드시 아래 명령어로 스타일을 맞춘 뒤 커밋한다.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew spotlessApply
```

- **새 코드**: spotlessApply 실행 후 `feat:`/`fix:`/`chore:` 커밋에 함께 포함
- **기존 코드 리포맷팅만 할 때**: `style:` 커밋으로 별도 분리

## 커밋 메시지 컨벤션

```
<type>: <설명>
```

| type | 용도 |
|---|---|
| `feat` | 새로운 기능 |
| `fix` | 버그 수정 |
| `chore` | 빌드 설정, 의존성 추가 등 기능/버그 외 변경 |
| `style` | 코드 포맷, 린터 등 로직 변경 없는 수정 |
| `refactor` | 기능 변경 없는 코드 구조 개선 |
| `test` | 테스트 추가/수정 |
| `docs` | 문서 수정 |
