package rrp.core.player;

import rrp.core.diplomacy.DiplomacyDecider;
import rrp.core.diplomacy.Treaty.State;

/**
 * How a village answers an emissary sent by a player ruler. Trade and alliance go through the same
 * {@link DiplomacyDecider} the autonomous villages use; a ceasefire is granted when the relation is not past
 * hopeless or the receiver is starving; an ultimatum needs grounds (a relation below the trade threshold).
 */
public final class Proposals {

    public enum Kind {
        TRADE, ALLIANCE, CEASEFIRE, ULTIMATUM;

        public static Kind parse(String s) {
            for (Kind k : values()) {
                if (k.name().equalsIgnoreCase(s)) {
                    return k;
                }
            }
            return null;
        }
    }

    public enum Answer { ACCEPTED, REFUSED, INVALID }

    private Proposals() {
    }

    public static Answer answer(Kind kind, State state, double index, boolean receiverShortage, DiplomacyDecider.Rules rules) {
        switch (kind) {
            case TRADE -> {
                if (state != State.PEACE) {
                    return Answer.INVALID;
                }
                return DiplomacyDecider.decide(new DiplomacyDecider.Situation(state, index, 0, false, false), rules)
                        == DiplomacyDecider.Action.PROPOSE_TRADE ? Answer.ACCEPTED : Answer.REFUSED;
            }
            case ALLIANCE -> {
                if (state == State.WAR || state == State.DEFENSIVE_ALLIANCE) {
                    return Answer.INVALID;
                }
                return DiplomacyDecider.decide(new DiplomacyDecider.Situation(state, index, 0, true, false), rules)
                        == DiplomacyDecider.Action.PROPOSE_ALLIANCE ? Answer.ACCEPTED : Answer.REFUSED;
            }
            case CEASEFIRE -> {
                if (state != State.WAR) {
                    return Answer.INVALID;
                }
                return receiverShortage || index >= 1.5 * rules.hostileAt() ? Answer.ACCEPTED : Answer.REFUSED;
            }
            default -> {
                if (state == State.WAR) {
                    return Answer.INVALID;
                }
                return index < rules.tradeAt() ? Answer.ACCEPTED : Answer.REFUSED;
            }
        }
    }
}
