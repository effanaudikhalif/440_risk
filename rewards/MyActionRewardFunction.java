package pas.risk.rewards;


// SYSTEM IMPORTS
import edu.bu.pas.risk.GameView;
import edu.bu.pas.risk.TerritoryOwnerView;
import edu.bu.pas.risk.action.Action;
import edu.bu.pas.risk.action.AttackAction;
import edu.bu.pas.risk.action.NoAction;
import edu.bu.pas.risk.agent.rewards.RewardFunction;
import edu.bu.pas.risk.agent.rewards.RewardType;
import edu.bu.pas.risk.territory.Continent;
import edu.bu.pas.risk.territory.Territory;


// JAVA PROJECT IMPORTS


/**
 * <p>Represents a function which punishes/pleasures your model according to how well the {@link Action}s its been
 * choosing have been. Your reward function could calculate R(s), R(s,a), or (R,s,a'): whichever is easiest for you to
 * think about (for instance does it make more sense to you to evaluate behavior when you see a state, the action you
 * took in that state, and how that action resolved? If so you want to pick R(s,a,s')).
 *
 * <p>By default this is configured to calculate R(s). If you want to change this you need to change the
 * {@link RewardType} enum in the constructor *and* you need to implement the corresponding method. Refer to
 * {@link RewardFunction} and {@link RewardType} for more details.
 */
public class MyActionRewardFunction
    extends RewardFunction<Action>
{
    private static final double TERRITORY_DELTA_WEIGHT = 20.0;
    private static final double CONTINENT_DELTA_WEIGHT = 60.0;
    private static final double ARMY_SHARE_DELTA_WEIGHT = 35.0;
    private static final double STRONGEST_OPPONENT_ARMY_SHARE_DELTA_WEIGHT = 20.0;
    private static final double CONTINENT_COMPLETION_DELTA_WEIGHT = 45.0;
    private static final double BORDER_VULNERABILITY_DELTA_WEIGHT = 25.0;
    private static final double SUCCESSFUL_ATTACK_BONUS = 12.0;
    private static final double FAILED_COSTLY_ATTACK_WEIGHT = 40.0;
    private static final double BREAK_OPPONENT_CONTINENT_WEIGHT = 25.0;
    private static final double NO_ACTION_WHEN_ATTACK_AVAILABLE_PENALTY = 15.0;
    private static final double TURN_TAX_PER_TURN = 30.0;


    // continet
    // territory 
    // army

    public MyActionRewardFunction(final int agentId)
    {
        super(RewardType.FULL_TRANSITION, agentId);
    }

    private static double safeDivide(final double numerator,
                                     final double denominator)
    {
        return denominator == 0.0 ? 0.0 : numerator / denominator;
    }

    private double clamp(final double value)
    {
        return Math.max(this.getLowerBound(), Math.min(this.getUpperBound(), value));
    }

    private double positivePart(final double value)
    {
        return Math.max(0.0, value);
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

    private int countTerritories(final GameView state,
                                 final int agentId)
    {
        return state.getTerritoriesOwnedBy(agentId).size();
    }

    private int countArmies(final GameView state,
                            final int agentId)
    {
        int total = 0;
        for(final TerritoryOwnerView ownerView : state.getTerritoryOwners())
        {
            if(ownerView.getOwner() == agentId)
            {
                total += ownerView.getArmies();
            }
        }
        return total;
    }

    private int countContinents(final GameView state,
                                final int agentId)
    {
        return state.getContinentsOwnedBy(agentId).size();
    }

    private int countFavorableAttacks(final GameView state)
    {
        int count = 0;
        for(final Territory territory : state.getTerritoriesOwnedBy(this.getAgentId()))
        {
            if(!this.isMine(state, territory))
            {
                continue;
            }

            final int myArmies = this.getOwnerView(state, territory).getArmies();
            if(myArmies <= 1)
            {
                continue;
            }

            for(final Territory adjacent : territory.adjacentTerritories())
            {
                if(this.isEnemy(state, adjacent) &&
                   this.getOwnerView(state, adjacent).getArmies() < myArmies)
                {
                    count += 1;
                }
            }
        }
        return count;
    }

    private int countTotalArmies(final GameView state)
    {
        int total = 0;
        for(final TerritoryOwnerView ownerView : state.getTerritoryOwners())
        {
            total += ownerView.getArmies();
        }
        return total;
    }

    private double getContinentCompletionScore(final GameView state)
    {
        double score = 0.0;

        for(final Continent continent : state.getBoard().continents())
        {
            int ownedTerritories = 0;
            final int continentSize = continent.territories().size();

            for(final Territory territory : continent.territories())
            {
                if(this.isMine(state, territory))
                {
                    ownedTerritories += 1;
                }
            }

            final double completion = safeDivide(ownedTerritories, continentSize);
            if(completion > 0.0)
            {
                score += Math.pow(completion, 2);
            }
        }

        return score;
    }

    private double getStrongestOpponentArmyShare(final GameView state)
    {
        final int totalArmies = this.countTotalArmies(state);
        double strongestShare = 0.0;

        for(int agentId = 0; agentId < state.getNumAgents(); ++agentId)
        {
            if(agentId == this.getAgentId())
            {
                continue;
            }

            strongestShare = Math.max(strongestShare,
                                      safeDivide(this.countArmies(state, agentId), totalArmies));
        }

        return strongestShare;
    }

    private int countTotalEnemyContinents(final GameView state)
    {
        int total = 0;

        for(int agentId = 0; agentId < state.getNumAgents(); ++agentId)
        {
            if(agentId == this.getAgentId())
            {
                continue;
            }

            total += this.countContinents(state, agentId);
        }

        return total;
    }

    private int countVulnerableTerritories(final GameView state)
    {
        int vulnerable = 0;

        for(final Territory territory : state.getTerritoriesOwnedBy(this.getAgentId()))
        {
            int myArmies = this.getOwnerView(state, territory).getArmies();
            int adjacentEnemyArmies = 0;
            boolean hasEnemyNeighbor = false;

            for(final Territory adjacent : territory.adjacentTerritories())
            {
                if(this.isEnemy(state, adjacent))
                {
                    hasEnemyNeighbor = true;
                    adjacentEnemyArmies += this.getOwnerView(state, adjacent).getArmies();
                }
            }

            if(hasEnemyNeighbor && adjacentEnemyArmies > myArmies)
            {
                vulnerable += 1;
            }
        }

        return vulnerable;
    }

    private double getBorderVulnerabilityRatio(final GameView state)
    {
        return safeDivide(this.countVulnerableTerritories(state),
                          this.countTerritories(state, this.getAgentId()));
    }

    public double getLowerBound() { return -100.0; }
    public double getUpperBound() { return 100.0; }

    /** {@inheritDoc} */
    public double getStateReward(final GameView state) { return Double.NEGATIVE_INFINITY; }

    /** {@inheritDoc} */
    public double getHalfTransitionReward(final GameView state,
                                          final Action action) { return Double.NEGATIVE_INFINITY; }

    /** {@inheritDoc} */
    public double getFullTransitionReward(final GameView state,
                                          final Action action,
                                          final GameView nextState)
    {
        double reward = 0.0;

        // ! DELTAS

        final int myTerritoriesBefore = this.countTerritories(state, this.getAgentId());
        final int myTerritoriesAfter = this.countTerritories(nextState, this.getAgentId());
        final int myArmiesBefore = this.countArmies(state, this.getAgentId());
        final int myArmiesAfter = this.countArmies(nextState, this.getAgentId());
        final int myContinentsBefore = this.countContinents(state, this.getAgentId());
        final int myContinentsAfter = this.countContinents(nextState, this.getAgentId());
        final int totalArmiesBefore = this.countTotalArmies(state);
        final int totalArmiesAfter = this.countTotalArmies(nextState);
        final int enemyContinentsBefore = this.countTotalEnemyContinents(state);
        final int enemyContinentsAfter = this.countTotalEnemyContinents(nextState);

        final double armyShareBefore = safeDivide(myArmiesBefore, totalArmiesBefore);
        final double armyShareAfter = safeDivide(myArmiesAfter, totalArmiesAfter);
        final double strongestOpponentArmyShareBefore = this.getStrongestOpponentArmyShare(state);
        final double strongestOpponentArmyShareAfter = this.getStrongestOpponentArmyShare(nextState);
        final double continentCompletionBefore = this.getContinentCompletionScore(state);
        final double continentCompletionAfter = this.getContinentCompletionScore(nextState);
        final double borderVulnerabilityBefore = this.getBorderVulnerabilityRatio(state);
        final double borderVulnerabilityAfter = this.getBorderVulnerabilityRatio(nextState);

        final double territoryDelta = (double)( myTerritoriesAfter - myTerritoriesBefore) / 42.0;
        final double continentDelta = (double) (myContinentsAfter - myContinentsBefore) / 6.0;
        final double armyLoss = (float) Math.max(0, myArmiesBefore - myArmiesAfter) / (double) totalArmiesAfter ;


        // ! COMPONENTS


        final double territoryComponent = TERRITORY_DELTA_WEIGHT * territoryDelta;
        final double continentComponent = CONTINENT_DELTA_WEIGHT * continentDelta;
        final double armyShareComponent = ARMY_SHARE_DELTA_WEIGHT * (armyShareAfter - armyShareBefore);
        final double strongestOpponentComponent = STRONGEST_OPPONENT_ARMY_SHARE_DELTA_WEIGHT
            * (strongestOpponentArmyShareBefore - strongestOpponentArmyShareAfter);
        final double continentCompletionComponent =
            CONTINENT_COMPLETION_DELTA_WEIGHT * (continentCompletionAfter - continentCompletionBefore);
        final double borderVulnerabilityComponent =
            BORDER_VULNERABILITY_DELTA_WEIGHT * (borderVulnerabilityBefore - borderVulnerabilityAfter);
        final double successfulAttackComponent =
            (action instanceof AttackAction && territoryDelta > 0) ? SUCCESSFUL_ATTACK_BONUS : 0.0;
        final double failedCostlyAttackPenalty =
            (action instanceof AttackAction && territoryDelta <= 0 && armyLoss > 0)
                ? FAILED_COSTLY_ATTACK_WEIGHT * safeDivide(armyLoss, totalArmiesBefore)
                : 0.0;
        final double breakOpponentContinentComponent =
            BREAK_OPPONENT_CONTINENT_WEIGHT * positivePart(enemyContinentsBefore - enemyContinentsAfter);
        final double noActionPenalty =
            (action instanceof NoAction && this.countFavorableAttacks(state) > 0)
                ? NO_ACTION_WHEN_ATTACK_AVAILABLE_PENALTY
                : 0.0;
        final double turnTaxPenalty =
            TURN_TAX_PER_TURN;


        // ! Adding to reward
            
        reward += territoryComponent;
        reward += continentComponent;
        reward += armyShareComponent;
        reward += strongestOpponentComponent;
        reward += continentCompletionComponent;
        reward += borderVulnerabilityComponent;
        reward += successfulAttackComponent;
        reward -= failedCostlyAttackPenalty;
        reward += breakOpponentContinentComponent;
        reward -= noActionPenalty;
        reward -= turnTaxPenalty;

        final double finalReward = this.clamp(reward);
        return finalReward;
    }

}

