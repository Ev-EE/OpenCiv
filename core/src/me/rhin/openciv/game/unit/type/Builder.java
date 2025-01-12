package me.rhin.openciv.game.unit.type;

import me.rhin.openciv.Civilization;
import me.rhin.openciv.asset.TextureEnum;
import me.rhin.openciv.game.AbstractAction;
import me.rhin.openciv.game.map.tile.ImprovementType;
import me.rhin.openciv.game.map.tile.Tile;
import me.rhin.openciv.game.map.tile.TileType.TileProperty;
import me.rhin.openciv.game.research.type.MiningTech;
import me.rhin.openciv.game.unit.Unit;
import me.rhin.openciv.game.unit.UnitItem;
import me.rhin.openciv.game.unit.UnitParameter;
import me.rhin.openciv.listener.SetTileTypeListener;
import me.rhin.openciv.listener.UnitActListener.UnitActEvent;
import me.rhin.openciv.listener.WorkTileListener;
import me.rhin.openciv.shared.packet.type.SetTileTypePacket;
import me.rhin.openciv.shared.packet.type.WorkTilePacket;

public class Builder extends UnitItem {

	public static class BuilderUnit extends Unit implements WorkTileListener, SetTileTypeListener {

		private ImprovementType improvementType;
		private boolean building;

		public BuilderUnit(UnitParameter unitParameter) {
			super(unitParameter, TextureEnum.UNIT_BUILDER);
			customActions.add(new FarmAction(this));
			this.canAttack = false;
			this.building = false;

			// FIXME: REALLY should remove this listener when this unit gets destroyed
			Civilization.getInstance().getEventManager().addListener(WorkTileListener.class, this);
		}

		@Override
		public void onWorkTile(WorkTilePacket packet) {
			if (packet.getUnitID() != getID())
				return;

			getStandingTile().setAppliedTurns(packet.getAppliedTurns());
		}

		@Override
		public void onSetTileType(SetTileTypePacket packet) {
			if (building) {
				// Assume we finish building
				building = false;
				improvementType = null;
			}

		}

		@Override
		public void moveToTargetTile() {
			super.moveToTargetTile();

			building = false;
		}

		@Override
		public int getMovementCost(Tile prevTile, Tile tile) {
			if (tile.containsTileProperty(TileProperty.WATER))
				return 1000000;
			else
				return tile.getMovementCost(prevTile);
		}

		@Override
		public int getCombatStrength() {
			return 0;
		}

		@Override
		public boolean isUnitCapturable() {
			return true;
		}

		public void setBuilding(boolean building) {
			this.building = building;
		}

		public boolean isBuilding() {
			return building;
		}

		public void setImprovementType(ImprovementType improvementType) {
			this.improvementType = improvementType;
		}

		public String getImprovementName() {
			return improvementType.getName();
		}

		public int getMaxTurns() {
			return improvementType.getMaxTurns();
		}
	}

	public static class FarmAction extends AbstractAction {

		public FarmAction(Unit unit) {
			super(unit);
		}

		@Override
		public boolean act(float delta) {
			// unit.getPlayerOwner().unselectUnit();
			unit.reduceMovement(2);
			WorkTilePacket packet = new WorkTilePacket();
			packet.setTile("farm", unit.getStandingTile().getGridX(), unit.getStandingTile().getGridY());
			Civilization.getInstance().getNetworkManager().sendPacket(packet);
			// unit.removeAction(this);

			BuilderUnit builderUnit = (BuilderUnit) unit;
			builderUnit.setBuilding(true);
			builderUnit.setImprovementType(ImprovementType.FARM);

			Civilization.getInstance().getEventManager().fireEvent(new UnitActEvent(unit));

			unit.removeAction(this);
			return true;
		}

		@Override
		public boolean canAct() {
			Tile tile = unit.getStandingTile();
			boolean farmableTile = !tile.isImproved() && tile.getBaseTileType().hasProperty(TileProperty.FARMABLE)
					&& tile.getTerritory() != null
					&& tile.getTerritory().getPlayerOwner().equals(unit.getPlayerOwner());

			BuilderUnit builderUnit = (BuilderUnit) unit;
			if (unit.getCurrentMovement() < 1 || !farmableTile || builderUnit.isBuilding()) {
				return false;
			}

			return true;
		}

		@Override
		public String getName() {
			return "Farm";
		}
	}

	public static class MineAction extends AbstractAction {

		public MineAction(Unit unit) {
			super(unit);
		}

		@Override
		public boolean act(float delta) {
			// unit.getPlayerOwner().unselectUnit();
			unit.reduceMovement(2);
			WorkTilePacket packet = new WorkTilePacket();
			packet.setTile("mine", unit.getStandingTile().getGridX(), unit.getStandingTile().getGridY());
			Civilization.getInstance().getNetworkManager().sendPacket(packet);
			// unit.removeAction(this);

			BuilderUnit builderUnit = (BuilderUnit) unit;
			builderUnit.setBuilding(true);
			builderUnit.setImprovementType(ImprovementType.MINE);

			Civilization.getInstance().getEventManager().fireEvent(new UnitActEvent(unit));

			unit.removeAction(this);
			return true;
		}

		@Override
		public boolean canAct() {
			Tile tile = unit.getStandingTile();

			if (!unit.getPlayerOwner().getResearchTree().hasResearched(MiningTech.class)) {
				return false;
			}
			
			boolean farmableTile = !tile.isImproved() && tile.containsTileProperty(TileProperty.MINEABLE)
					&& tile.getTerritory() != null
					&& tile.getTerritory().getPlayerOwner().equals(unit.getPlayerOwner());

			BuilderUnit builderUnit = (BuilderUnit) unit;
			if (unit.getCurrentMovement() < 1 || !farmableTile || builderUnit.isBuilding()) {
				return false;
			}

			return true;
		}

		@Override
		public String getName() {
			return "Mine";
		}
	}

//TODO: Mine action, plantation action

	@Override
	public int getProductionCost() {
		return 50;
	}

	@Override
	public boolean meetsProductionRequirements() {
		return true;
	}

	@Override
	public String getName() {
		return "Builder";
	}

	@Override
	public TextureEnum getTexture() {
		return TextureEnum.UNIT_BUILDER;
	}
}
