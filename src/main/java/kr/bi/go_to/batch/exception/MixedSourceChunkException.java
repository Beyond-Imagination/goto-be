package kr.bi.go_to.batch.exception;

public class MixedSourceChunkException extends RuntimeException {
    public MixedSourceChunkException() {
        super("동일한 청크 내에는 단일 데이터 소스(source)의 아이템만 포함되어야 합니다.");
    }
}
