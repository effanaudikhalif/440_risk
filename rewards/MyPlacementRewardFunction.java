package pas.risk.rewards;


// SYSTEM IMPORTS
import edu.bu.pas.risk.GameView;
import edu.bu.pas.risk.TerritoryOwnerView;
import edu.bu.pas.risk.agent.rewards.RewardFunction;
import edu.bu.pas.risk.agent.rewards.RewardType;
import edu.bu.pas.risk.territory.Territory;


// JAVA PROJECT IMPORTS


/**
 * <p>Represents a function which punishes/pleasures your model according to how well the {@link Territory}s its been
 * choosing to place armies have been. Your reward function could calculate R(s), R(s,t), or (R,t,a'): whichever
 * is easiest for you to think about (for instance does it make more sense to you to evaluate behavior when you see a
 * state, the action you took in that state, and how that action resolved? If so you want to pick R(s,t,s')).
 *
 * <p>By default this is configured to calculate R(s). If you want to change this you need to change the
 * {@link RewardType} enum in the constructor *and* you need to implement the corresponding method. Refer to
 * {@link RewardFunction} and {@link RewardType} for more details.
 */
public class MyPlacementRewardFunction
    extends RewardFunction<Territory>
{
    private static final double BASE_REWARD = 10.0;
    private static final double BORDER_BONUS = 20.0;
    private static final double UNDERDEFENDED_BONUS = 20.0;
    private static final double OUTNUMBERED_FRONT_BONUS = 10.0;
    private static final double QUIET_TERRITORY_PENALTY = 10.0;
    private static final double OVERDEFENDED_TERRITORY_PENALTY = 10.0;
    private static final double CONTINENT_COMPLETION_WEIGHT = 10.0;
    private static final double HIGH_COMPLETION_CONTINENT_BONUS_WEIGHT = 5.0;

    public MyPlacementRewardFunction(final int agentId)
    {
        super(RewardType.HALF_TRANSITION, agentId);
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

    private int countAdjacentEnemyTerritories(final GameView state,
                                              final Territory territory)
    {
        int count = 0;
        for(final Territory adjacent : territory.adjacentTerritories())
        {
            if(this.isEnemy(state, adjacent))
            {
                count += 1;
            }
        }
        return count;
    }

    private int sumAdjacentEnemyArmies(final GameView state,
                                       final Territory territory)
    {
        int total = 0;
        for(final Territory adjacent : territory.adjacentTerritories())
        {
            if(this.isEnemy(state, adjacent))
            {
                total += this.getOwnerView(state, adjacent).getArmies();
            }
        }
        return total;
    }

    private int sumAdjacentFriendlyArmies(final GameView state,
                                          final Territory territory)
    {
        int total = 0;
        for(final Territory adjacent : territory.adjacentTerritories())
        {
            if(this.isMine(state, adjacent))
            {
                total += this.getOwnerView(state, adjacent).getArmies();
            }
        }
        return total;
    }

    private double getContinentCompletion(final GameView state,
                                          final Territory territory)
    {
        int ownedCount = 0;
        final int continentSize = territory.continent().territories().size();

        for(final Territory continentTerritory : territory.continent().territories())
        {
            if(this.isMine(state, continentTerritory))
            {
                ownedCount += 1;
            }
        }

        return continentSize == 0 ? 0.0 : (double)ownedCount / continentSize;
    }

    private double clamp(final double reward)
    {
        return Math.max(this.getLowerBound(), Math.min(this.getUpperBound(), reward));
    }

    public double getLowerBound() { return -25.0; }
    public double getUpperBound() { return 100.0; }

    /** {@inheritDoc} */
    public double getStateReward(final GameView state) { return Double.NEGATIVE_INFINITY; }

    /** {@inheritDoc} */
    public double getHalfTransitionReward(final GameView state,
                                          final Territory action)
    {
        if(action == null)
        {
            return this.getLowerBound();
        }

        if(!this.isMine(state, action))
        {
            return this.getLowerBound();
        }

        double reward = BASE_REWARD;

        final int currentArmies = this.getOwnerView(state, action).getArmies();
        final int adjacentEnemyCount = this.countAdjacentEnemyTerritories(state, action);
        final int adjacentEnemyArmies = this.sumAdjacentEnemyArmies(state, action);
        final int adjacentFriendlyArmies = this.sumAdjacentFriendlyArmies(state, action);
        final double continentCompletion = this.getContinentCompletion(state, action);
        final int continentArmyValue = action.continent().armiesPerTurn();
        final double borderComponent = adjacentEnemyCount > 0 ? BORDER_BONUS : 0.0;
        final double underdefendedComponent = adjacentEnemyArmies > currentArmies ? UNDERDEFENDED_BONUS : 0.0;
        final double outnumberedFrontComponent = adjacentEnemyArmies > adjacentFriendlyArmies ? OUTNUMBERED_FRONT_BONUS : 0.0;
        final double continentCompletionComponent = CONTINENT_COMPLETION_WEIGHT * continentCompletion * continentArmyValue;
        final double highCompletionComponent = continentCompletion >= 0.75
            ? HIGH_COMPLETION_CONTINENT_BONUS_WEIGHT * continentArmyValue
            : 0.0;
        final double quietPenalty = adjacentEnemyCount == 0 ? QUIET_TERRITORY_PENALTY : 0.0;
        final double overdefendedPenalty =
            (adjacentEnemyCount > 0 && currentArmies > adjacentEnemyArmies + 5) ? OVERDEFENDED_TERRITORY_PENALTY : 0.0;

        reward += borderComponent;
        reward += underdefendedComponent;
        reward += outnumberedFrontComponent;
        reward += continentCompletionComponent;
        reward += highCompletionComponent;
        reward -= quietPenalty;
        reward -= overdefendedPenalty;

        final double finalReward = this.clamp(reward);
        return finalReward;
    }

    /** {@inheritDoc} */
    public double getFullTransitionReward(final GameView state,
                                          final Territory action,
                                          final GameView nextState) { return Double.NEGATIVE_INFINITY; }

}

