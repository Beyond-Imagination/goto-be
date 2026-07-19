package kr.bi.go_to.controller.map;

import java.util.List;
import kr.bi.go_to.service.FloorMapService;
import kr.bi.go_to.spec.PlaceFloorApiSpec;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places/{placeId}/floors")
public class PlaceFloorController implements PlaceFloorApiSpec {

    private final FloorMapService floorMapService;

    public PlaceFloorController(FloorMapService floorMapService) {
        this.floorMapService = floorMapService;
    }

    @GetMapping
    @Override
    public List<Integer> listFloors(@PathVariable Long placeId) {
        return floorMapService.listFloorLevels(placeId);
    }
}
