package kr.bi.go_to.savedplace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kr.bi.go_to.config.ClockConfig;
import kr.bi.go_to.config.JpaAuditConfig;
import kr.bi.go_to.enums.Role;
import kr.bi.go_to.model.member.Member;
import kr.bi.go_to.model.place.Place;
import kr.bi.go_to.model.savedplace.SavedPlace;
import kr.bi.go_to.repository.MemberRepository;
import kr.bi.go_to.repository.PlaceRepository;
import kr.bi.go_to.repository.SavedPlaceRepository;
import kr.bi.go_to.support.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({
    TestcontainersConfiguration.class,
    ClockConfig.class,
    JpaAuditConfig.class,
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SavedPlaceRepositoryTest {

    @Autowired
    SavedPlaceRepository savedPlaceRepository;

    @Autowired
    PlaceRepository placeRepository;

    @Autowired
    MemberRepository memberRepository;

    Member member;
    Place place;

    @BeforeEach
    void setUp() {
        savedPlaceRepository.deleteAll();
        placeRepository.deleteAll();
        memberRepository.deleteAll();

        member = memberRepository.save(new Member(Role.USER, "tester"));
        place = placeRepository.save(Place.builder()
                .externalId("test-place-1")
                .source("TEST")
                .name("테스트 장소")
                .build());
    }

    @Test
    @DisplayName("저장된 장소면 존재 여부가 true다")
    void existsByMemberAndPlaceWhenSaved() {
        savedPlaceRepository.save(
                SavedPlace.builder().member(member).place(place).build());

        boolean exists = savedPlaceRepository.existsByMember_IdAndPlace_Id(member.getId(), place.getId());

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("저장되지 않은 장소면 존재 여부가 false다")
    void existsByMemberAndPlaceWhenNotSaved() {
        boolean exists = savedPlaceRepository.existsByMember_IdAndPlace_Id(member.getId(), place.getId());

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("같은 회원-장소 조합을 중복 저장하면 유니크 제약 위반이 발생한다")
    void throwsWhenSavingDuplicateMemberAndPlace() {
        savedPlaceRepository.save(
                SavedPlace.builder().member(member).place(place).build());

        assertThatThrownBy(() -> savedPlaceRepository.saveAndFlush(
                        SavedPlace.builder().member(member).place(place).build()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("저장된 장소를 삭제하면 목록에서 사라진다")
    void deletesByMemberAndPlace() {
        savedPlaceRepository.save(
                SavedPlace.builder().member(member).place(place).build());

        savedPlaceRepository.deleteByMember_IdAndPlace_Id(member.getId(), place.getId());

        assertThat(savedPlaceRepository.existsByMember_IdAndPlace_Id(member.getId(), place.getId()))
                .isFalse();
    }

    @Test
    @DisplayName("저장돼 있지 않은 장소를 삭제해도 예외가 발생하지 않는다")
    void deletingUnsavedPlaceDoesNotThrow() {
        savedPlaceRepository.deleteByMember_IdAndPlace_Id(member.getId(), place.getId());

        assertThat(savedPlaceRepository.existsByMember_IdAndPlace_Id(member.getId(), place.getId()))
                .isFalse();
    }

    @Test
    @DisplayName("최근 저장한 순으로 목록을 반환한다")
    void returnsSavedPlacesOrderedByCreatedAtDesc() {
        Place olderPlace = placeRepository.save(Place.builder()
                .externalId("test-place-2")
                .source("TEST")
                .name("먼저 저장한 장소")
                .build());
        SavedPlace older = savedPlaceRepository.saveAndFlush(
                SavedPlace.builder().member(member).place(olderPlace).build());
        SavedPlace newer = savedPlaceRepository.saveAndFlush(
                SavedPlace.builder().member(member).place(place).build());

        List<SavedPlace> found = savedPlaceRepository.findByMember_IdOrderByCreatedAtDesc(member.getId());

        assertThat(found).extracting(SavedPlace::getId).containsExactly(newer.getId(), older.getId());
    }
}
