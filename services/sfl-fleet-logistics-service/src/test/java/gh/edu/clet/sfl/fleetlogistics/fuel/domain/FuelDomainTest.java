package gh.edu.clet.sfl.fleetlogistics.fuel.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.RecordMetadata;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SiteCode;
import gh.edu.clet.sfl.fleetlogistics.fleet.domain.model.SourceChannel;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.DriverLogbook;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelAnomalyCase;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelPolicy;
import gh.edu.clet.sfl.fleetlogistics.fuel.domain.model.FuelTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FuelDomainTest {
    private static final Instant NOW=Instant.parse("2026-07-22T08:00:00Z");
    private static RecordMetadata metadata(){return RecordMetadata.createdBy("driver-1",NOW,SourceChannel.WEB,"fuel-test");}

    @Test void monetary_total_is_exact_and_card_is_masked(){var tx=new FuelTransaction(UUID.randomUUID(),SiteCode.of("ACCRA"),"P-1","MANUAL",UUID.randomUUID(),UUID.randomUUID(),null,NOW,"VENDOR",null,"DIESEL",new BigDecimal("12.345"),"LITRE",new BigDecimal("10.0000"),new BigDecimal("123.45"),Currency.getInstance("GHS"),"1234567890123456",1000,null,null,FuelTransaction.Status.RECEIVED,FuelTransaction.Lifecycle.ACTIVE,NOW,"key",metadata());assertThat(tx.totalCost()).isEqualByComparingTo("123.45");assertThat(tx.maskedCardReference()).isEqualTo("****3456");}
    @Test void inconsistent_total_is_rejected(){assertThatThrownBy(()->new FuelTransaction(UUID.randomUUID(),SiteCode.of("ACCRA"),null,"MANUAL",UUID.randomUUID(),UUID.randomUUID(),null,NOW,"V",null,"DIESEL",new BigDecimal("2"),"L",new BigDecimal("10"),new BigDecimal("99"),Currency.getInstance("GHS"),null,1,null,null,FuelTransaction.Status.RECEIVED,FuelTransaction.Lifecycle.ACTIVE,NOW,"k",metadata())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("totalCost");}
    @Test void policy_is_effective_dated_and_normalises_allowlists(){var p=new FuelPolicy(UUID.randomUUID(),SiteCode.of("ACCRA"),"Default",NOW,null,1,new BigDecimal("100"),null,null,new BigDecimal("120"),null,null,500,true,24,new BigDecimal("500"),8,Set.of("diesel"),Set.of("vendor"),FuelPolicy.Status.ACTIVE,metadata());assertThat(p.appliesAt(NOW.plusSeconds(1))).isTrue();assertThat(p.allowsProduct("DIESEL")).isTrue();assertThat(p.allowsVendor("Vendor")).isTrue();}
    @Test void approved_logbook_is_locked_until_privileged_reopen(){var l=logbook().submit(NOW.plusSeconds(1),metadata()).startReview(metadata()).approved(NOW.plusSeconds(2),"ok",metadata());assertThat(l.status()).isEqualTo(DriverLogbook.Status.APPROVED);assertThatThrownBy(()->l.submit(NOW,metadata())).isInstanceOf(IllegalStateException.class);assertThat(l.reopened("audit correction",metadata()).status()).isEqualTo(DriverLogbook.Status.REOPENED);}
    @Test void logbook_odometer_cannot_regress(){assertThatThrownBy(()->new DriverLogbook(UUID.randomUUID(),"LOG-1",SiteCode.of("ACCRA"),UUID.randomUUID(),UUID.randomUUID(),null,LocalDate.now(),NOW,NOW.plusSeconds(10),"A","B",null,DriverLogbook.UseClassification.OFFICIAL,"Work",null,100L,99L,true,null,DriverLogbook.Status.DRAFT,null,null,null,null,metadata())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("regresses");}
    @Test void anomaly_cannot_close_without_explanation_decision_and_evidence(){var a=anomaly().assign("manager",metadata()).review(metadata()).decide(FuelAnomalyCase.Decision.APPROVED,"valid exception",metadata());assertThatThrownBy(()->a.close("done",UUID.randomUUID(),metadata())).isInstanceOf(IllegalStateException.class).hasMessageContaining("explanation");var ready=anomaly().assign("manager",metadata()).review(metadata()).requestExplanation(metadata()).explain("route diversion",UUID.randomUUID(),metadata()).review(metadata()).decide(FuelAnomalyCase.Decision.APPROVED,"accepted",metadata());assertThat(ready.close("complete",UUID.randomUUID(),metadata()).status()).isEqualTo(FuelAnomalyCase.Status.CLOSED);}
    @Test void anomaly_supports_reassign_hold_resume_and_cancel(){
        var reassigned=anomaly().assign("officer-1",metadata()).reassign("officer-2",metadata());
        assertThat(reassigned.status()).isEqualTo(FuelAnomalyCase.Status.ASSIGNED);
        assertThat(reassigned.assignee()).isEqualTo("officer-2");
        var held=reassigned.review(metadata()).hold("await fuel receipt",metadata());
        assertThat(held.status()).isEqualTo(FuelAnomalyCase.Status.HELD);
        assertThat(held.resume(metadata()).status()).isEqualTo(FuelAnomalyCase.Status.UNDER_REVIEW);
        assertThat(anomaly().cancel("raised in error",metadata()).status()).isEqualTo(FuelAnomalyCase.Status.CANCELLED);
        assertThatThrownBy(()->anomaly().resume(metadata())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->anomaly().assign("x",metadata()).hold("",metadata())).isInstanceOf(IllegalArgumentException.class);
    }
    private static DriverLogbook logbook(){return new DriverLogbook(UUID.randomUUID(),"LOG-1",SiteCode.of("ACCRA"),UUID.randomUUID(),UUID.randomUUID(),null,LocalDate.of(2026,7,22),NOW,NOW.plusSeconds(3600),"A","B",null,DriverLogbook.UseClassification.OFFICIAL,"Work",null,100,120L,true,null,DriverLogbook.Status.DRAFT,null,null,null,null,metadata());}
    private static FuelAnomalyCase anomaly(){return new FuelAnomalyCase(UUID.randomUUID(),"ANM-1",SiteCode.of("ACCRA"),UUID.randomUUID(),null,UUID.randomUUID(),UUID.randomUUID(),null,FuelAnomalyCase.Type.LIMIT_EXCEEDED,FuelAnomalyCase.Severity.HIGH,true,FuelAnomalyCase.Status.DETECTED,null,NOW.plusSeconds(3600),null,null,null,null,0,List.of("MAX_PER_TRANSACTION"),metadata());}
}
