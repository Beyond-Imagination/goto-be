package kr.bi.go_to.controller.help;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.help.request.CreateHelpRequestRequest;
import kr.bi.go_to.controller.help.response.HelpRequestResponse;
import kr.bi.go_to.controller.help.response.NearbyHelpRequestResponse;
import kr.bi.go_to.service.HelpRequestService;
import kr.bi.go_to.spec.HelpRequestApiSpec;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/help-requests")
public class HelpRequestController implements HelpRequestApiSpec {

    private final HelpRequestService helpRequestService;

    public HelpRequestController(HelpRequestService helpRequestService) {
        this.helpRequestService = helpRequestService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public HelpRequestResponse create(
            @AuthenticationPrincipal AuthenticatedMember member, @Valid @RequestBody CreateHelpRequestRequest request) {
        return helpRequestService.create(member.id(), request);
    }

    @GetMapping("/nearby")
    @Override
    public List<NearbyHelpRequestResponse> findNearby(
            @AuthenticationPrincipal AuthenticatedMember member,
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @RequestParam(defaultValue = "1000") @Min(1) @Max(5000) int radiusMeters) {
        return helpRequestService.findNearby(member.id(), latitude, longitude, radiusMeters);
    }

    @GetMapping("/me")
    @Override
    public List<HelpRequestResponse> findMine(@AuthenticationPrincipal AuthenticatedMember member) {
        return helpRequestService.findMine(member.id());
    }

    @GetMapping("/{id}")
    @Override
    public HelpRequestResponse get(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        return helpRequestService.get(member.id(), id);
    }

    @PostMapping("/{id}/accept")
    @Override
    public HelpRequestResponse accept(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        return helpRequestService.accept(member.id(), id);
    }

    @PostMapping("/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Override
    public void reject(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        helpRequestService.reject(member.id(), id);
    }

    @PostMapping("/{id}/complete")
    @Override
    public HelpRequestResponse complete(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        return helpRequestService.complete(member.id(), id);
    }

    @PostMapping("/{id}/cancel")
    @Override
    public HelpRequestResponse cancel(@AuthenticationPrincipal AuthenticatedMember member, @PathVariable UUID id) {
        return helpRequestService.cancel(member.id(), id);
    }
}
