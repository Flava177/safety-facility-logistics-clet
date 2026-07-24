package gh.edu.clet.sfl.fleetlogistics.fuel.api;
import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.fleetlogistics.fleet.api.FleetActorResolver;
import gh.edu.clet.sfl.fleetlogistics.fuel.application.service.FuelImportService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/v1/fuel/imports") @io.swagger.v3.oas.annotations.tags.Tag(name="Fuel Integrations")
public class FuelImportController {private final FuelImportService imports;private final FleetActorResolver actors;public FuelImportController(FuelImportService i,FleetActorResolver a){imports=i;actors=a;}@PostMapping(value="/csv",consumes="multipart/form-data")public ApiResponse<FuelImportService.ImportResult> csv(@RequestParam String siteCode,@RequestParam String sourceSystem,@RequestPart MultipartFile file,HttpServletRequest h)throws IOException{return ApiResponse.ok(imports.importCsv(siteCode,sourceSystem,file.getOriginalFilename(),file.getBytes(),actors.resolve(h)));}}
