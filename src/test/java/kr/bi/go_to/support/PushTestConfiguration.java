package kr.bi.go_to.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 푸시 발송을 기록만 하는 채널로 바꿔 끼운다. 테스트에서 Firebase 자격 증명이 필요 없어진다.
 *
 * <p>RecordingPushSender 자체가 PushSender라서 빈 하나면 충분하다. 빈 이름은 PushConfig의
 * {@code pushSender}와 겹치지 않게 둔다(겹치면 정의 충돌로 컨텍스트가 뜨지 않는다).
 */
@TestConfiguration(proxyBeanMethods = false)
public class PushTestConfiguration {

    @Bean
    @Primary
    public RecordingPushSender recordingPushSender() {
        return new RecordingPushSender();
    }
}
