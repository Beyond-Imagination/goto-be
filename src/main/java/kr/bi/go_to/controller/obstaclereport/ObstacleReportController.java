package kr.bi.go_to.controller.obstaclereport;

import jakarta.validation.Valid;
import java.util.List;
import kr.bi.go_to.config.security.AuthenticatedMember;
import kr.bi.go_to.controller.obstaclereport.request.CreateObstacleReportRequest;
import kr.bi.go_to.controller.obstaclereport.request.ObstacleReportClusterRequest;
import kr.bi.go_to.controller.obstaclereport.request.UpdateObstacleReportStatusRequest;
import kr.bi.go_to.controller.obstaclereport.response.ObstacleReportClusterResponse;
import kr.bi.go_to.controller.obstaclereport.response.ObstacleReportResponse;
import kr.bi.go_to.service.obstaclereport.ObstacleReportService;
import kr.bi.go_to.spec.ObstacleReportApiSpec;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/obstacle-reports")
public class ObstacleReportController implements ObstacleReportApiSpec {

    private final ObstacleReportService obstacleReportService;

    public ObstacleReportController(ObstacleReportService obstacleReportService) {
        this.obstacleReportService = obstacleReportService;
    }

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ObstacleReportResponse create(
            @AuthenticationPrincipal AuthenticatedMember member,
            @Valid @RequestBody CreateObstacleReportRequest request) {
        return obstacleReportService.create(member.id(), request);
    }

    @Override
    @GetMapping("/{id}")
    public ObstacleReportResponse get(@PathVariable Long id) {
        return obstacleReportService.get(id);
    }

    @Override
    @PostMapping("/{id}/status")
    public ObstacleReportResponse updateStatus(
            @AuthenticationPrincipal AuthenticatedMember member,
            @PathVariable Long id,
            @Valid @RequestBody UpdateObstacleReportStatusRequest request) {
        return obstacleReportService.updateStatus(member.id(), id, request);
    }

    @Override
    @GetMapping("/clusters")
    public List<ObstacleReportClusterResponse> getClusters(
            @Valid @ParameterObject @ModelAttribute ObstacleReportClusterRequest request) {
        return obstacleReportService.getClusters(request);
    }
}
