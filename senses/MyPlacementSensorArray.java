package pas.risk.senses;


// SYSTEM IMPORTS
import edu.bu.jmat.Matrix;

import edu.bu.pas.risk.GameView;
import edu.bu.pas.risk.TerritoryOwnerView;
import edu.bu.pas.risk.agent.senses.PlacementSensorArray;
import edu.bu.pas.risk.territory.Territory;


// JAVA PROJECT IMPORTS


/**
 * A suite of sensors to convert a {@link Territory} into a feature vector (must be a row-vector)
 */
public class MyPlacementSensorArray
    extends PlacementSensorArray
{

    public static final int NUM_FEATURES = 8;

    public MyPlacementSensorArray(final int agentId)
    {
        super(agentId);
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

    public Matrix getSensorValues(final GameView state,
                                  final int numRemainingArmies,
                                  final Territory territory)
    {
        final Matrix features = Matrix.zeros(1, NUM_FEATURES);

        final TerritoryOwnerView ownerView = this.getOwnerView(state, territory);
        final int currentArmies = ownerView.getArmies();
        int adjacentEnemyTerritories = 0;
        int adjacentFriendlyTerritories = 0;
        int totalAdjacentEnemyArmies = 0;
        int totalAdjacentFriendlyArmies = 0;

        for(final Territory adjacent : territory.adjacentTerritories())
        {
            final TerritoryOwnerView adjacentOwnerView = this.getOwnerView(state, adjacent);

            if(this.isEnemy(state, adjacent))
            {
                adjacentEnemyTerritories += 1;
                totalAdjacentEnemyArmies += adjacentOwnerView.getArmies();
            } else if(this.isMine(state, adjacent))
            {
                adjacentFriendlyTerritories += 1;
                totalAdjacentFriendlyArmies += adjacentOwnerView.getArmies();
            }
        }

        int ownedInContinent = 0;
        final int continentSize = territory.continent().territories().size();
        for(final Territory continentTerritory : territory.continent().territories())
        {
            if(this.isMine(state, continentTerritory))
            {
                ownedInContinent += 1;
            }
        }

        final double continentCompletion = continentSize == 0 ? 0.0 : (double)ownedInContinent / continentSize;
        final double defensiveAdvantage = (currentArmies - totalAdjacentEnemyArmies) / 50.0;

        features.set(0, 0, currentArmies / 50.0);
        features.set(0, 1, numRemainingArmies / 20.0);
        features.set(0, 2, adjacentEnemyTerritories / 6.0);
        features.set(0, 3, adjacentFriendlyTerritories / 6.0);
        features.set(0, 4, totalAdjacentEnemyArmies / 50.0);
        features.set(0, 5, totalAdjacentFriendlyArmies / 50.0);
        features.set(0, 6, defensiveAdvantage);
        features.set(0, 7, continentCompletion);

        System.out.println("[PlacementSensor] " + territory.name() + " " + features);

        return features; // row vector
    }

}

