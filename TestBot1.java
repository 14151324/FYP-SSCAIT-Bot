import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import bwapi.*;
import bwta.BWTA;
import bwta.BaseLocation;
import bwta.Chokepoint;

public class TestBot1 extends DefaultBWListener {

    private Mirror mirror = new Mirror();

    private Game game;

    private Player self;
    
    private boolean firstTimeSetup = false;
    
    //private HashSet enemyBuildingMemory = new HashSet();
    private ArrayList<Position> enemyBuildingMemory = new ArrayList<Position>();
    private ArrayList<BaseLocation> bases = new ArrayList<BaseLocation>();
    private BaseLocation homeLoc = null;
    private Chokepoint homeEnt = null;
    private int baseLocChecked = 0;
    private BaseLocation enemyBaseLocation = null;
    
    private static final int maxGasWorkersPerBuilding = 3;
    
    private int gatewayCount = 0;
    private boolean cyberCoreBuilt = false;
    private boolean assimilatorBuilt = false;
    
    public static void main(String[] args) {
        new TestBot1().run();
    }
    
    private static void executeInCommandLine(String command) {
    	try {
    		Process process = Runtime.getRuntime().exec(command);
    	} catch (Exception err) {
    		err.printStackTrace();
    	}
    }
    
	// Returns a suitable TilePosition to build a given building type near
	// specified TilePosition aroundTile, or null if not found. (builder parameter is our worker)
	public TilePosition getBuildTile(Unit builder, UnitType buildingType, TilePosition aroundTile) {
		TilePosition ret = null;
		int maxDist = 3;
		int stopDist = 40;
		// Refinery, Assimilator, Extractor
		if (buildingType.isRefinery()) {
	 		for (Unit n : game.neutral().getUnits()) {
	 			if ((n.getType() == UnitType.Resource_Vespene_Geyser) &&
	 					( Math.abs(n.getTilePosition().getX() - aroundTile.getX()) < stopDist ) &&
	 					( Math.abs(n.getTilePosition().getY() - aroundTile.getY()) < stopDist )
	 					) return n.getTilePosition();
	 		}
	 	}
		
		else {
			while ((maxDist < stopDist) && (ret == null)) {
		 		for (int i=aroundTile.getX()-maxDist; i<=aroundTile.getX()+maxDist; i++) {
		 			for (int j=aroundTile.getY()-maxDist; j<=aroundTile.getY()+maxDist; j++) {
		 				if (game.canBuildHere(new TilePosition(i,j), buildingType, builder, false)) {
		 					// units that are blocking the tile
		 					boolean unitsInWay = false;
		 					for (Unit u : game.getAllUnits()) {
		 						if (u.getID() == builder.getID()) continue;
		 						if ((Math.abs(u.getTilePosition().getX()-i) < 4) && (Math.abs(u.getTilePosition().getY()-j) < 4)) unitsInWay = true;
		 					}
		 					if (!unitsInWay) {
		 						return new TilePosition(i, j);
		 					}
		 					// creep for Zerg
		 					if (buildingType.requiresCreep()) {
		 						boolean creepMissing = false;
		 						for (int k=i; k<=i+buildingType.tileWidth(); k++) {
		 							for (int l=j; l<=j+buildingType.tileHeight(); l++) {
		 								if (!game.hasCreep(k, l)) creepMissing = true;
		 								break;
		 							}
		 						}
		 						if (creepMissing) continue;
		 					}
		 				}
		 			}
		 		}
		 		maxDist += 2;
		 	}
		}
	
	 	//if (ret == null) game.printf("Unable to find suitable build position for "+buildingType.toString());
	 	return ret;
	}

	public void attackClosestUnit(Unit myUnit) {
		Unit closestEnemyUnit = null;
		if(!myUnit.isAttacking()) {
			for(Unit enemyUnit : game.enemy().getUnits()) {
				if(closestEnemyUnit == null || myUnit.getDistance(enemyUnit) < myUnit.getDistance(closestEnemyUnit)) {
					closestEnemyUnit = enemyUnit;
				}
			}
			if(closestEnemyUnit != null)
				myUnit.attack(closestEnemyUnit);
		}

	}
	
	public void explore(Unit myUnit) {
		myUnit.move(bases.get(1).getPosition());
	}
	
	public void gatherMinerals(Unit myUnit) {
		//find the closest mineral
        Unit closestMineral = null;
        for(Unit neutralUnit : game.neutral().getUnits()) {
            if(neutralUnit.getType().isMineralField()) {
                if(closestMineral == null || myUnit.getDistance(neutralUnit) < myUnit.getDistance(closestMineral)) {
                    closestMineral = neutralUnit;
                }
            }
        }
        //if a mineral patch was found, send the worker to gather it
        if(closestMineral != null) {
            myUnit.gather(closestMineral, false);
        }
	}
	
	public void gatherGas(Unit myUnit) {
		//find the closest mineral
        Unit closestGas = null;
        for(Unit gasUnit : self.getUnits()) {
            if (gasUnit.getType().isRefinery()) {
                if(closestGas == null || myUnit.getDistance(gasUnit) < myUnit.getDistance(closestGas)) {
                	closestGas = gasUnit;
                }
            }
        }
        //if a mineral patch was found, send the worker to gather it
        if(closestGas != null) {
            myUnit.gather(closestGas, false);
        }
	}
	
	public void run() {
    	//executeInCommandLine("taskkill /f /im Starcraft.exe");
    	//executeInCommandLine("taskkill /f /im Chaoslauncher.exe");
    	//try {
    	 	//Thread.sleep(250);
    		//executeInCommandLine("F:\\SSCAI\\BWAPI\\BWAPI\\Chaoslauncher\\Chaoslauncher.exe");
    	//} catch (InterruptedException ex) {
    		
    	//}
    	
        mirror.getModule().setEventListener(this);
        mirror.startGame();
    }

    @Override
    public void onUnitCreate(Unit unit) {
        System.out.println("New unit discovered " + unit.getType());
        if (unit.getType() == UnitType.Protoss_Gateway) {
        	gatewayCount++;
        }
        if (unit.getType() == UnitType.Protoss_Cybernetics_Core) {
        	cyberCoreBuilt = true;
        }
    }

    @Override
    public void onStart() {
        game = mirror.getGame();
        self = game.self();

        game.setLocalSpeed(0);
        //Use BWTA to analyze map
        //This may take a few minutes if the map is processed first time!
        System.out.println("Analyzing map...");
        BWTA.readMap();
        BWTA.analyze();
        System.out.println("Map data ready");
        
        System.out.println("THIS IS SUPPOSED TO BE THE LAST LINE PRINTED");
        
        int i = 0;
        for(BaseLocation baseLocation : BWTA.getBaseLocations()){
        	System.out.println("Base location #" + (++i) + ". Printing location's region polygon:");
        	for(Position position : baseLocation.getRegion().getPolygon().getPoints()){
        		System.out.print(position + ", ");
        	}
        	
        }
        System.out.println("THIS IS SUPPOSED TO BE THE LAST LINE PRINTED");
    }

    @Override
    public void onFrame() {
    	
    	if (firstTimeSetup == false) {
        	game.sendText("Good Luck, Have Fun");
        	for (BaseLocation b : BWTA.getBaseLocations()) {
        		// If this is a possible start location,
        		if (b.isStartLocation()) {
        			bases.add(b);
        		}
        	}
        	firstTimeSetup = true;
        }
    
    	//game.setTextSize(10);

        StringBuilder units = new StringBuilder("My units:\n");

        List<Unit> workers = new ArrayList<>();
        List<Unit> minWorkers = new ArrayList<>();
        List<Unit> gasWorkers = new ArrayList<>();
        List<Unit> gateways = new ArrayList<>();
        List<Unit> army = new ArrayList<>();
        Unit mainBase = null;
        Unit builder = null;
        Unit scout = null;
                
        //iterate through my units and subdivide them into groups
        for(Unit myUnit : self.getUnits()) {
            units.append(myUnit.getType()).append(" ").append(myUnit.getTilePosition()).append("\n");
            game.drawTextMap(myUnit.getPosition().getX(), myUnit.getPosition().getY(), myUnit.getOrder().toString());
            if(myUnit.getOrderTargetPosition().getX() != 0 && myUnit.getOrderTargetPosition().getY() != 0)
            	game.drawLineMap(myUnit.getPosition(), myUnit.getOrderTargetPosition(), bwapi.Color.Green);
            
        	if(myUnit.getType().isWorker()) {
        		if(builder == null) {
        			builder = myUnit;
        		}
        		//else if(scout == null && builder != myUnit)
        			//scout = myUnit;
        		else if(assimilatorBuilt && gasWorkers.size() < 2) {
        			gasWorkers.add(myUnit);
        		}
        		else {
        			minWorkers.add(myUnit);
        		}
        	}
        	if(myUnit.getType() == UnitType.Protoss_Nexus) {
        		mainBase = myUnit;
        	}
        	if(myUnit.getType() == UnitType.Protoss_Gateway) {
        		gateways.add(myUnit);
        	}        	
        	if(myUnit.getType() == UnitType.Protoss_Zealot || myUnit.getType() == UnitType.Protoss_Dragoon) {
        		army.add(myUnit);
        	}
        	if(myUnit.getType() == UnitType.Protoss_Assimilator && !myUnit.isBeingConstructed()) {
        		assimilatorBuilt = true;
        	}
        }
        //builder handler
        if(!builder.isConstructing()) {	
	        if(builder.isIdle() || builder.isGatheringMinerals()) {
	        	if(self.minerals() >= 200 && !cyberCoreBuilt && !gateways.isEmpty()) {
	        		TilePosition buildTile = getBuildTile(builder, UnitType.Protoss_Cybernetics_Core, self.getStartLocation());
	    			if (buildTile != null) 
	    				builder.build(UnitType.Protoss_Cybernetics_Core, buildTile);
	        	}
	        	else if(self.minerals() >= 150 && gateways.size() < 4) {
	        		TilePosition buildTile = getBuildTile(builder, UnitType.Protoss_Gateway, self.getStartLocation());
	        			if (buildTile != null) 
	        				builder.build(UnitType.Protoss_Gateway, buildTile);
	        	}
	        	else if(self.minerals() >= 100 && self.supplyUsed() >= 26 && !assimilatorBuilt) {
	        		TilePosition buildTile = getBuildTile(builder, UnitType.Protoss_Assimilator, self.getStartLocation());
	    			if (buildTile != null) 
	    				builder.build(UnitType.Protoss_Assimilator, buildTile);
	        	}
	        	else if(self.minerals() >= 100 && self.supplyTotal() - self.supplyUsed() <= 4) {
	        		TilePosition buildTile = getBuildTile(builder, UnitType.Protoss_Pylon, self.getStartLocation());
	    			if (buildTile != null) 
	    				builder.build(UnitType.Protoss_Pylon, buildTile);
	        	}
	        	else if(builder.isIdle()) {
	        		gatherMinerals(builder);
	        	}
	        }
        }
        //scout handler
        //currently broken
        /*
        if(self.supplyUsed() >= 9 && enemyBuildingMemory.isEmpty())
        	explore(scout);
        else
        	gatherMinerals(scout);
        */
        //Probe handling
        for(Unit worker : gasWorkers) {
        	if (!worker.isGatheringGas()) {
        		gatherGas(worker);
        	}
        	else if (worker.isIdle())
        		gatherMinerals(worker);
        }        
        for(Unit worker : minWorkers) {
        	if (!worker.isGatheringMinerals()) {
        		gatherMinerals(worker);
        	}
        }
        //gateway handling
        for(Unit gateway : gateways) {
        	if(self.minerals() >= 125 && self.gas() >= 50 && self.supplyTotal() - self.supplyUsed() > 4 && gateway.isIdle()) {
        		gateway.train(UnitType.Protoss_Dragoon);
        	}
        }
        //army handler
        for(Unit myUnit : army) {
        	if(myUnit.isUnderAttack()) {
        		attackClosestUnit(myUnit);
    		}
        	else {
        		if(enemyBaseLocation != null) {
        			myUnit.move(enemyBaseLocation.getPosition());
        		}
        		else if(enemyBuildingMemory.isEmpty() && myUnit.isIdle()) {
            		explore(myUnit);
            	}
            	//else {
            		//for(Position p : enemyBuildingMemory) {
            		//	myUnit.attack(p);
            		//}
            		//Unit nearestEnemyUnit = getClosestEnemy(myUnit);
            		//myUnit.attack(nearestEnemyUnit);
            		//attackClosestUnit(myUnit);
            	//}
            	
        	}
        	
        }
        	
        //if there's enough minerals, train an SCV
        if(self.minerals() >= 50 && self.supplyTotal() - self.supplyUsed() >= 2 && workers.size() < 20 && mainBase.isIdle() && !cyberCoreBuilt) {
        	mainBase.train(UnitType.Protoss_Probe);
        }
        
        
        //always loop over all currently visible enemy units (even though this set is usually empty)
        for(Unit u : game.enemy().getUnits()) {
        	//if this unit is in fact a building
        	if (u.getType().isBuilding()) {
        		//check if we have it's position in memory and add it if we don't
        		if (!enemyBuildingMemory.contains(u.getPosition())) {
        			enemyBuildingMemory.add(u.getPosition());
        			System.out.println(u.getType() + " added to enemy building list");
        		}
        	}
        }

        //loop over all the positions that we remember
        for(Position p : enemyBuildingMemory) {
        	// compute the TilePosition corresponding to our remembered Position p
        	TilePosition tileCorrespondingToP = new TilePosition(p.getX()/32 , p.getY()/32);
        	
        	//if that tile is currently visible to us...
        	if(game.isVisible(tileCorrespondingToP)) {

        		//loop over all the visible enemy buildings and find out if at least
        		//one of them is still at that remembered position
        		boolean buildingStillThere = false;
        		for(Unit u : game.enemy().getUnits()) {
        			if((u.getType().isBuilding()) && (u.getPosition().equals(p))) {
        				if(enemyBaseLocation == null && BWTA.getNearestBaseLocation(u.getPosition()).isStartLocation()) {
        	        		enemyBaseLocation = BWTA.getNearestBaseLocation(u.getPosition());
        	        	}
        				buildingStillThere = true;
        				break;
        			}
        		}

        		//if there is no more any building, remove that position from our memory
        		if(buildingStillThere == false) {
        			enemyBuildingMemory.remove(p);
        			break;
        		}
        	}
        }

        for(BaseLocation b : bases) {
        	if(game.isVisible(b.getTilePosition()) && enemyBuildingMemory.isEmpty()) {
        		bases.remove(b);
        	}
        }
        
        if(homeLoc == null)
        	homeLoc = BWTA.getNearestBaseLocation(mainBase.getPosition());
        if(homeEnt == null)
        	homeEnt = BWTA.getNearestChokepoint(mainBase.getPosition());
        //draw my units on screen
        //game.drawTextScreen(10, 25, bases.toString());

        game.drawTextScreen(10, 25, army.toString());
        
    }
}