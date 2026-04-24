package pas.risk.senses;


// SYSTEM IMPORTS
import edu.bu.jmat.Matrix;

import edu.bu.pas.risk.GameView;
import edu.bu.pas.risk.TerritoryOwnerView;
import edu.bu.pas.risk.agent.senses.StateSensorArray;
import edu.bu.pas.risk.territory.Continent;
import edu.bu.pas.risk.territory.Territory;
import edu.bu.pas.risk.territory.TerritoryCard;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


// JAVA PROJECT IMPORTS


/**
 * A suite of sensors to convert a {@link GameView} into a feature vector (must be a row-vector)
 */
public class MyStateSensorArray
    extends StateSensorArray
{
    public static final int NUM_FEATURES = 23;

    public MyStateSensorArray(final int agentId)
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

    private int getArmies(final GameView state,
                          final Territory territory)
    {
        return this.getOwnerView(state, territory).getArmies();
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
                total += this.getArmies(state, adjacent);
            }
        }
        return total;
    }

    private boolean isBorder(final GameView state,
                             final Territory territory)
    {
        if(!this.isMine(state, territory))
        {
            return false;
        }
        return this.countAdjacentEnemyTerritories(state, territory) > 0;
    }

    public Matrix getSensorValues(final GameView state)
    {
        final Matrix features = Matrix.zeros(1, NUM_FEATURES);

        final int agentId = this.getAgentId();
        final int totalTerritories = state.getBoard().territories().size();
        final int totalContinents = state.getBoard().continents().size();
        final List<Territory> myTerritories = state.getTerritoriesOwnedBy(agentId);
        final List<Continent> myContinents = state.getContinentsOwnedBy(agentId);
        final List<TerritoryCard> myCards = state.getAgentInventory(agentId);

        int totalArmies = 0;
        int myArmies = 0;
        int strongestEnemyArmies = 0;
        int strongestEnemyTerritories = 0;
        int maxEnemyContinents = 0;
        int wildCardCount = 0;

        final Map<Integer, Integer> enemyArmyCounts = new HashMap<Integer, Integer>();
        final Map<Integer, Integer> enemyTerritoryCounts = new HashMap<Integer, Integer>();

        for(final TerritoryOwnerView ownerView : state.getTerritoryOwners())
        {
            final int owner = ownerView.getOwner();
            final int armies = ownerView.getArmies();

            totalArmies += armies;

            if(owner == agentId)
            {
                myArmies += armies;
            } else if(!ownerView.isUnclaimed())
            {
                enemyArmyCounts.put(owner, enemyArmyCounts.getOrDefault(owner, 0) + armies);
                enemyTerritoryCounts.put(owner, enemyTerritoryCounts.getOrDefault(owner, 0) + 1);
            }
        }

        for(final int enemyId : enemyArmyCounts.keySet())
        {
            strongestEnemyArmies = Math.max(strongestEnemyArmies, enemyArmyCounts.get(enemyId));
            strongestEnemyTerritories = Math.max(strongestEnemyTerritories, enemyTerritoryCounts.getOrDefault(enemyId, 0));
            maxEnemyContinents = Math.max(maxEnemyContinents, state.getContinentsOwnedBy(enemyId).size());
        }

        for(final TerritoryCard card : myCards)
        {
            if(card.isWild())
            {
                wildCardCount += 1;
            }
        }

        double maxMyContinentCompletion = 0.0;
        double maxEnemyContinentCompletion = 0.0;
        double myContinentCompletionSum = 0.0;

        for(final Continent continent : state.getBoard().continents())
        {
            final int continentSize = continent.territories().size();
            int myOwnedInContinent = 0;
            final Map<Integer, Integer> enemyOwnedCounts = new HashMap<Integer, Integer>();

            for(final Territory territory : continent.territories())
            {
                final TerritoryOwnerView ownerView = this.getOwnerView(state, territory);
                final int owner = ownerView.getOwner();

                if(owner == agentId)
                {
                    myOwnedInContinent += 1;
                } else if(!ownerView.isUnclaimed())
                {
                    enemyOwnedCounts.put(owner, enemyOwnedCounts.getOrDefault(owner, 0) + 1);
                }
            }

            int maxEnemyOwnedInContinent = 0;
            for(final int enemyOwned : enemyOwnedCounts.values())
            {
                maxEnemyOwnedInContinent = Math.max(maxEnemyOwnedInContinent, enemyOwned);
            }

            final double myCompletion = safeDivide(myOwnedInContinent, continentSize);
            final double enemyCompletion = safeDivide(maxEnemyOwnedInContinent, continentSize);

            maxMyContinentCompletion = Math.max(maxMyContinentCompletion, myCompletion);
            maxEnemyContinentCompletion = Math.max(maxEnemyContinentCompletion, enemyCompletion);
            myContinentCompletionSum += myCompletion;
        }

        int borderTerritories = 0;
        int exposedTerritories = 0;
        int totalAdjacentEnemyArmies = 0;
        int attackOpportunities = 0;
        int favorableAttackOpportunities = 0;
        int vulnerableTerritories = 0;
        int borderTerritoriesWithPressure = 0;
        double armyCompetitionRatioSum = 0.0;
        double maxLocalArmyAdvantage = 0.0;
        boolean sawBorderEdge = false;
        final Set<Territory> uniqueHostileNeighbors = new HashSet<Territory>();

        for(final Territory territory : myTerritories)
        {
            final int myTerritoryArmies = this.getArmies(state, territory);
            final int adjacentEnemyCount = this.countAdjacentEnemyTerritories(state, territory);
            final int adjacentEnemyArmies = this.sumAdjacentEnemyArmies(state, territory);

            if(this.isBorder(state, territory))
            {
                borderTerritories += 1;
                exposedTerritories += 1;
                totalAdjacentEnemyArmies += adjacentEnemyArmies;

                if(adjacentEnemyArmies > myTerritoryArmies)
                {
                    vulnerableTerritories += 1;
                }

                if(adjacentEnemyArmies > 0)
                {
                    borderTerritoriesWithPressure += 1;
                    armyCompetitionRatioSum += safeDivide(myTerritoryArmies, adjacentEnemyArmies);
                }
            }

            attackOpportunities += adjacentEnemyCount;

            for(final Territory adjacent : territory.adjacentTerritories())
            {
                if(this.isEnemy(state, adjacent))
                {
                    final int enemyArmies = this.getArmies(state, adjacent);
                    // final double localArmyAdvantage = myTerritoryArmies - enemyArmies;

                    uniqueHostileNeighbors.add(adjacent);

                    if(myTerritoryArmies > 1.5 * enemyArmies)
                    {
                        favorableAttackOpportunities += 1;
                    }

                    // if(!sawBorderEdge || localArmyAdvantage > maxLocalArmyAdvantage)
                    // {
                    //     maxLocalArmyAdvantage = localArmyAdvantage;
                    //     sawBorderEdge = true;
                    // }
                }
            }
        }

        features.set(0, 0, safeDivide(myTerritories.size(), totalTerritories));
        features.set(0, 1, safeDivide(myArmies, totalArmies));
        features.set(0, 2, safeDivide(myContinents.size(), totalContinents));
        features.set(0, 3, safeDivide(state.getBonusArmiesFor(agentId), 20.0));
        features.set(0, 4, safeDivide(myCards.size(), 5.0));
        features.set(0, 5, safeDivide(wildCardCount, 5.0));
        features.set(0, 6, TerritoryCard.hasValidTrade(myCards) ? 1.0 : 0.0);
        features.set(0, 7, safeDivide(strongestEnemyArmies, totalArmies));
        features.set(0, 8, safeDivide(strongestEnemyTerritories, totalTerritories));
        features.set(0, 9, safeDivide(maxEnemyContinents, totalContinents));
        features.set(0, 10, maxMyContinentCompletion);
        features.set(0, 11, maxEnemyContinentCompletion);
        features.set(0, 12, safeDivide(myContinentCompletionSum, totalContinents));
        features.set(0, 13, safeDivide(borderTerritories, myTerritories.size()));
        features.set(0, 14, safeDivide(exposedTerritories, myTerritories.size()));
        features.set(0, 15, safeDivide(uniqueHostileNeighbors.size(), totalTerritories));
        features.set(0, 16, safeDivide(totalAdjacentEnemyArmies, totalArmies));
        features.set(0, 17, safeDivide(favorableAttackOpportunities, attackOpportunities));
        features.set(0, 18, safeDivide(vulnerableTerritories, myTerritories.size()));
        features.set(0, 19, safeDivide(armyCompetitionRatioSum, borderTerritoriesWithPressure));
        // features.set(0, 20, sawBorderEdge ? safeDivide(maxLocalArmyAdvantage, totalArmies) : 0.0);
        // features.set(0, 21, safeDivide(state.getNumTurns(), 100.0));
        // features.set(0, 22, state.isOver() ? 1.0 : 0.0);

        System.out.println("[StateSensor] "
            + "territoryShare=" + features.get(0, 0)
            + ", armyShare=" + features.get(0, 1)
            + ", continentShare=" + features.get(0, 2)
            + ", bonusArmies=" + features.get(0, 3)
            + ", cardCount=" + features.get(0, 4)
            + ", canTrade=" + features.get(0, 6)
            + ", strongestEnemyArmyShare=" + features.get(0, 7)
            + ", continentCompletionMax=" + features.get(0, 10)
            + ", borderRatio=" + features.get(0, 13)
            + ", favorableAttackRatio=" + features.get(0, 17)
            + ", vulnerableRatio=" + features.get(0, 18));

        return features;
    }

}
