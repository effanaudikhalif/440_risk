package pas.risk.senses;


// SYSTEM IMPORTS
import edu.bu.jmat.Matrix;

import edu.bu.pas.risk.GameView;
import edu.bu.pas.risk.TerritoryOwnerView;
import edu.bu.pas.risk.action.Action;
import edu.bu.pas.risk.action.AttackAction;
import edu.bu.pas.risk.action.FortifyAction;
import edu.bu.pas.risk.action.NoAction;
import edu.bu.pas.risk.action.RedeemCardsAction;
import edu.bu.pas.risk.agent.senses.ActionSensorArray;
import edu.bu.pas.risk.territory.Continent;
import edu.bu.pas.risk.territory.Territory;


// JAVA PROJECT IMPORTS


/**
 * A suite of sensors to convert a {@link Action} into a feature vector (must be a row-vector)
 */ 
public class MyActionSensorArray
    extends ActionSensorArray
{

    public static final int NUM_FEATURES = 12;

    public MyActionSensorArray(final int agentId)
    {
        super(agentId);
    }

    private static double safeDivide(final double numerator,
                                     final double denominator)
    {
        return denominator == 0.0 ? 0.0 : numerator / denominator;
    }

    private TerritoryOwnerView getOwnerView(final GameView state,
                                            final Territory territory)
    {
        return state.getTerritoryOwners().get(territory);
    }

    private boolean isMine(final GameView state,
                           final Territory territory)
    {
        return this.getOwnerView(state, territory).getOwner() == this.getAgentId();
    }

    private boolean isEnemy(final GameView state,
                            final Territory territory)
    {
        final TerritoryOwnerView ownerView = this.getOwnerView(state, territory);
        return ownerView.getOwner() != this.getAgentId() && !ownerView.isUnclaimed();
    }

    private boolean targetCompletesContinent(final GameView state,
                                             final Territory target)
    {
        final Continent continent = target.continent();
        for(final Territory territory : continent.territories())
        {
            if(territory.equals(target) || this.isMine(state, territory))
            {
                continue;
            }

            if(this.isEnemy(state, territory))
            {
                return false;
            }
        }
        return true;
    }

    public Matrix getSensorValues(final GameView state,
                                  final int actionCounter,
                                  final Action action)
    {
        final Matrix features = Matrix.zeros(1, NUM_FEATURES);

        final boolean isAttack = action instanceof AttackAction;
        final boolean isFortify = action instanceof FortifyAction;
        final boolean isRedeem = action instanceof RedeemCardsAction;
        final boolean isNoAction = action instanceof NoAction;

        features.set(0, 0, isAttack ? 1.0 : 0.0);
        features.set(0, 1, isFortify ? 1.0 : 0.0);
        features.set(0, 2, isRedeem ? 1.0 : 0.0);
        features.set(0, 3, isNoAction ? 1.0 : 0.0);
        features.set(0, 4, action.isTerminal() ? 1.0 : 0.0);
        features.set(0, 5, actionCounter / 20.0);

        Territory source = null;
        Territory target = null;
        double committedArmies = 0.0;

        if(isAttack)
        {
            final AttackAction attack = (AttackAction)action;
            source = attack.from();
            target = attack.to();
            committedArmies = attack.attackingArmies();
        } else if(isFortify)
        {
            final FortifyAction fortify = (FortifyAction)action;
            source = fortify.from();
            target = fortify.to();
            committedArmies = fortify.deltaArmies();
        }

        if(source != null && target != null)
        {
            final double sourceArmies = this.getOwnerView(state, source).getArmies();
            final double targetArmies = this.getOwnerView(state, target).getArmies();

            features.set(0, 6, sourceArmies / 50.0);
            features.set(0, 7, targetArmies / 50.0);
            features.set(0, 8, committedArmies / 10.0);
            features.set(0, 9, (sourceArmies - targetArmies) / 50.0);
            features.set(0, 10, safeDivide(committedArmies, targetArmies));
            features.set(0, 11, this.targetCompletesContinent(state, target) ? 1.0 : 0.0);
        }

        return features; // row vector
    }

}

