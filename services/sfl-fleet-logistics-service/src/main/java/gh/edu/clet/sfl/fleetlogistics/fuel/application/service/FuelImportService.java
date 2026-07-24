package gh.edu.clet.sfl.fleetlogistics.fuel.application.service;

import gh.edu.clet.sfl.common.security.ActorContext;
import gh.edu.clet.sfl.common.security.SflPermission;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/** Provider-neutral, auditable CSV adapter. Vendor-specific layouts belong in separate adapters. */
@Service
public class FuelImportService {
    private final FuelApplicationService fuel; private final FuelAccessPolicy access; private final JdbcTemplate jdbc; private final ObjectMapper json;
    public FuelImportService(FuelApplicationService f,FuelAccessPolicy a,JdbcTemplate j,ObjectMapper o){fuel=f;access=a;jdbc=j;json=o;}
    public ImportResult importCsv(String site,String source,String fileName,byte[] content,ActorContext actor){access.require(actor,SflPermission.FUEL_TRANSACTION_IMPORT,site,"FuelImportBatch",null);UUID batch=UUID.randomUUID();String hash=sha256(content);List<String> lines=new String(content,StandardCharsets.UTF_8).lines().filter(l->!l.isBlank()).toList();if(lines.size()<2)throw new IllegalArgumentException("CSV must contain a header and at least one row");List<String> headers=parse(lines.get(0));List<RowResult> results=new ArrayList<>();int accepted=0;for(int n=1;n<lines.size();n++){Map<String,String> row=map(headers,parse(lines.get(n)));try{var tx=fuel.capture(toCommand(site,source,row,batch+"-"+n,actor));results.add(new RowResult(n+1,"ACCEPTED",tx.id(),null,null));accepted++;}catch(RuntimeException e){results.add(new RowResult(n+1,"REJECTED",null,"FUEL_IMPORT_ROW_INVALID",e.getMessage()));}}
        jdbc.update("INSERT INTO fleet_logistics.fuel_import_batches(id,site_code,source_system,file_name,file_hash,status,total_rows,accepted_rows,rejected_rows,submitted_by,submitted_at,correlation_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",batch,site,source,fileName,hash,accepted==results.size()?"COMPLETED":"COMPLETED_WITH_ERRORS",results.size(),accepted,results.size()-accepted,actor.actorId(),java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC),actor.correlationId());
        for(var r:results)jdbc.update("INSERT INTO fleet_logistics.fuel_import_rows(id,batch_id,row_number,status,transaction_id,error_code,error_message,raw_record) VALUES (?,?,?,?,?,?,?,?::jsonb)",UUID.randomUUID(),batch,r.rowNumber(),r.status(),r.transactionId(),r.errorCode(),r.errorMessage(),"{}");return new ImportResult(batch,results.size(),accepted,results.size()-accepted,results);}
    private FuelApplicationService.CaptureFuel toCommand(String site,String source,Map<String,String> r,String key,ActorContext actor){return new FuelApplicationService.CaptureFuel(site,r.get("providerTransactionId"),source,uuid(r,"vehicleId",true),uuid(r,"driverId",true),uuid(r,"tripId",false),Instant.parse(required(r,"occurredAt")),required(r,"vendorReference"),r.get("stationReference"),required(r,"fuelProduct"),new BigDecimal(required(r,"quantity")),required(r,"quantityUnit"),new BigDecimal(required(r,"unitPrice")),decimal(r.get("totalCost")),required(r,"currency"),r.get("cardReference"),Long.parseLong(required(r,"odometerReading")),uuid(r,"receiptEvidenceId",false),r.get("comments"),key,actor,SourceChannel.IMPORT);}
    private static List<String> parse(String line){List<String> out=new ArrayList<>();StringBuilder v=new StringBuilder();boolean quoted=false;for(int i=0;i<line.length();i++){char c=line.charAt(i);if(c=='"'){if(quoted&&i+1<line.length()&&line.charAt(i+1)=='"'){v.append('"');i++;}else quoted=!quoted;}else if(c==','&&!quoted){out.add(v.toString().strip());v.setLength(0);}else v.append(c);}out.add(v.toString().strip());return out;}
    private static Map<String,String> map(List<String> h,List<String> v){if(h.size()!=v.size())throw new IllegalArgumentException("CSV row column count does not match header");Map<String,String> m=new LinkedHashMap<>();for(int i=0;i<h.size();i++)m.put(h.get(i),v.get(i).isBlank()?null:v.get(i));return m;}
    private static String required(Map<String,String> r,String k){String v=r.get(k);if(v==null||v.isBlank())throw new IllegalArgumentException(k+" is required");return v;}
    private static UUID uuid(Map<String,String> r,String k,boolean required){String v=r.get(k);if(v==null||v.isBlank()){if(required)throw new IllegalArgumentException(k+" is required");return null;}return UUID.fromString(v);}
    private static BigDecimal decimal(String v){return v==null||v.isBlank()?null:new BigDecimal(v);}
    private static String sha256(byte[] bytes){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}catch(Exception e){throw new IllegalStateException(e);}}
    public record ImportResult(UUID batchId,int totalRows,int acceptedRows,int rejectedRows,List<RowResult> rows){}
    public record RowResult(int rowNumber,String status,UUID transactionId,String errorCode,String errorMessage){}
}
