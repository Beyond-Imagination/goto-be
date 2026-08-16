package kr.bi.go_to.controller.member;

import kr.bi.go_to.controller.member.response.NicknameAvailabilityResponse;
import kr.bi.go_to.service.NicknameService;
import kr.bi.go_to.spec.MemberApiSpec;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class MemberController implements MemberApiSpec {

    private final NicknameService nicknameService;

    public MemberController(NicknameService nicknameService) {
        this.nicknameService = nicknameService;
    }

    @GetMapping("/nicknames/{nickname}/availability")
    @Override
    public NicknameAvailabilityResponse checkNicknameAvailability(@PathVariable String nickname) {
        return new NicknameAvailabilityResponse(nicknameService.isAvailable(nickname));
    }
}
