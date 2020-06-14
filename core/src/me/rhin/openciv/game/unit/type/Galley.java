package me.rhin.openciv.game.unit.type;

import me.rhin.openciv.asset.TextureEnum;
import me.rhin.openciv.game.map.tile.Tile;
import me.rhin.openciv.game.player.Player;
import me.rhin.openciv.game.unit.Unit;
import me.rhin.openciv.game.unit.UnitParameter;

public class Galley extends Unit {

	public Galley(UnitParameter unitParameter) {
		super(unitParameter, TextureEnum.UNIT_SETTLER);
	}

	@Override
	public int getMovementCost(Tile tile) {
		if (!tile.getTileType().isWater())
			return 1000000;
		else
			return tile.getTileType().getMovementCost();
	}
}