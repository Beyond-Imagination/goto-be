package kr.bi.go_to.controller.map;

import kr.bi.go_to.model.map.FloorGeoJson;
import kr.bi.go_to.service.FloorMapService;
import kr.bi.go_to.spec.IndoorMapApiSpec;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/places/{placeId}/floors/{floor}")
public class IndoorMapController implements IndoorMapApiSpec {

    private final FloorMapService floorMapService;

    public IndoorMapController(FloorMapService floorMapService) {
        this.floorMapService = floorMapService;
    }

    @GetMapping("/indoor-map")
    @Override
    public ResponseEntity<FloorGeoJson> getIndoorMap(@PathVariable Long placeId, @PathVariable Integer floor) {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("application/geo+json"))
                .body(floorMapService.getIndoorMap(placeId, floor));
    }
}
