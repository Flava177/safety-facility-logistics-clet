package gh.edu.clet.sfl.facilities.cleaning.api;

import gh.edu.clet.sfl.common.api.ApiResponse;
import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningCommands;
import gh.edu.clet.sfl.facilities.cleaning.application.CleaningVendorService;
import gh.edu.clet.sfl.facilities.shared.domain.audit.SourceChannel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cleaning vendors, their contracted SLA terms and their scorecards - SRS-SFL-S169-03.
 *
 * <p>Vendor Master (S133) is not integrated. {@code /vendor-master-references} records a reference as
 * known to S133, pending a real integration; registering a vendor resolves against it and is refused
 * with {@code CLEANING_VENDOR_NOT_FOUND} otherwise. Every scorecard response carries
 * {@code vendorMasterStatus} so a client cannot mistake the stand-in for an integration.
 */
@RestController
@RequestMapping("/api/v1/facilities/cleaning")
@Tag(name = "S169 Cleaning Vendors", description = "Vendors, SLA terms and SLA scorecards")
public class CleaningVendorController {

    private final CleaningVendorService vendors;

    public CleaningVendorController(CleaningVendorService vendors) {
        this.vendors = vendors;
    }

    @PostMapping("/vendor-master-references")
    @Operation(summary = "Record a reference as known to Vendor Master (S133)",
            description = "S133 is not built; this is the local table an authorised user maintains until it is - "
                    + "see the gap report. Idempotent on (site, reference).")
    public ResponseEntity<ApiResponse<CleaningResponses.VendorMasterReferenceResponse>> recordMasterReference(
            @Valid @RequestBody CleaningRequests.RecordVendorMasterReference request, ActorContext actor,
            SourceChannel channel) {
        var recorded = vendors.recordMasterReference(new CleaningCommands.RecordVendorMasterReference(
                request.siteCode(), request.reference(), request.legalName(), request.evidenceNote(), actor, channel));
        return ResponseEntity.status(201).body(ApiResponse.ok(
                CleaningResponses.VendorMasterReferenceResponse.from(recorded, vendors.vendorMasterStatus())));
    }

    @GetMapping("/vendor-master-references")
    @Operation(summary = "References recorded as known to Vendor Master (S133) at a site")
    public ApiResponse<List<CleaningResponses.VendorMasterReferenceResponse>> knownReferences(
            @RequestParam String siteCode, ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(vendors.knownReferences(siteCode, actor, channel).stream()
                .map(record -> CleaningResponses.VendorMasterReferenceResponse.from(record, vendors.vendorMasterStatus()))
                .toList());
    }

    @PostMapping("/vendors")
    @Operation(summary = "Register a cleaning vendor from a known Vendor Master reference")
    public ResponseEntity<ApiResponse<CleaningResponses.VendorResponse>> register(
            @Valid @RequestBody CleaningRequests.RegisterVendor request, ActorContext actor, SourceChannel channel) {
        var vendor = vendors.register(new CleaningCommands.RegisterVendor(request.siteCode(),
                request.vendorMasterReference(), actor, channel));
        return ResponseEntity.created(URI.create("/api/v1/facilities/cleaning/vendors/" + vendor.id()))
                .body(ApiResponse.ok(CleaningResponses.VendorResponse.from(vendor)));
    }

    @PatchMapping("/vendors/{vendorId}/status")
    @Operation(summary = "Activate or suspend a vendor")
    public ApiResponse<CleaningResponses.VendorResponse> changeStatus(@PathVariable UUID vendorId,
            @Valid @RequestBody CleaningRequests.ChangeVendorStatus request, ActorContext actor,
            SourceChannel channel) {
        return ApiResponse.ok(CleaningResponses.VendorResponse.from(
                vendors.changeStatus(new CleaningCommands.ChangeVendorStatus(vendorId, request.status(), actor,
                        channel))));
    }

    @GetMapping("/vendors")
    @Operation(summary = "Cleaning vendors at a site")
    public ApiResponse<List<CleaningResponses.VendorResponse>> vendors(@RequestParam String siteCode,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(vendors.vendors(siteCode, actor, channel).stream()
                .map(CleaningResponses.VendorResponse::from).toList());
    }

    @PostMapping("/vendors/{vendorId}/sla-terms")
    @Operation(summary = "Set a new version of a vendor's contracted SLA terms",
            description = "Response time, completion time, quality-rating floor - versioned; the new version "
                    + "closes the one in force (SRS-SFL-S169-03).")
    public ResponseEntity<ApiResponse<CleaningResponses.SlaTermsResponse>> setTerms(@PathVariable UUID vendorId,
            @Valid @RequestBody CleaningRequests.SetSlaTerms request, ActorContext actor, SourceChannel channel) {
        var terms = vendors.setTerms(new CleaningCommands.SetSlaTerms(vendorId, request.responseMinutes(),
                request.completionMinutes(), request.qualityFloor(), actor, channel));
        return ResponseEntity.status(201).body(ApiResponse.ok(CleaningResponses.SlaTermsResponse.from(terms)));
    }

    @GetMapping("/vendors/{vendorId}/sla-terms")
    @Operation(summary = "A vendor's SLA terms history, oldest first")
    public ApiResponse<List<CleaningResponses.SlaTermsResponse>> termsHistory(@PathVariable UUID vendorId,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(vendors.termsHistory(vendorId, actor, channel).stream()
                .map(CleaningResponses.SlaTermsResponse::from).toList());
    }

    @GetMapping("/vendors/{vendorId}/scorecard")
    @Operation(summary = "A vendor's SLA scorecard",
            description = "Computed from the tasks' own lifecycle timestamps and occupant ratings - never "
                    + "self-reported (SRS-SFL-S169-03).")
    public ApiResponse<CleaningResponses.ScorecardResponse> scorecard(@PathVariable UUID vendorId,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            ActorContext actor, SourceChannel channel) {
        return ApiResponse.ok(CleaningResponses.ScorecardResponse.from(
                vendors.scorecard(vendorId, from, to, actor, channel)));
    }
}
