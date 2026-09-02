package gh.edu.clet.sfl.safetysecurity.incident.domain.model;

import java.util.Objects;

/**
 * A likelihood x impact risk rating, banded via a fixed default matrix - SRS §D.9 step 2.
 *
 * <h2>The matrix is a provisional default, not a CLET-approved standard</h2>
 *
 * SRS §D.9's open question Q-163-1 ("What is the CLET severity and risk-rating matrix?") is
 * unresolved - HSE and the business have not supplied real likelihood x impact bands. Building
 * "mandatory CAPA blocks closure" testably needs *some* banding, so this class uses a standard 5x5
 * ordinal-product matrix (score 1-25, quartile-ish bands) as a clearly-labelled placeholder. It is a
 * single static table in one place, easy to find and replace with CLET's real matrix once Q-163-1 is
 * answered - nothing else in the domain depends on the specific thresholds, only on {@link #band()}
 * returning a {@link RiskBand}.
 */
public record RiskRating(Likelihood likelihood, Impact impact) {

    public RiskRating {
        Objects.requireNonNull(likelihood, "likelihood is required");
        Objects.requireNonNull(impact, "impact is required");
    }

    /** 1-25, the product of each axis's 1-based ordinal. */
    public int score() {
        return (likelihood.ordinal() + 1) * (impact.ordinal() + 1);
    }

    /** Provisional banding pending Q-163-1 - see the class Javadoc. */
    public RiskBand band() {
        int score = score();
        if (score <= 4) {
            return RiskBand.LOW;
        }
        if (score <= 9) {
            return RiskBand.MEDIUM;
        }
        if (score <= 15) {
            return RiskBand.HIGH;
        }
        return RiskBand.CRITICAL;
    }
}
