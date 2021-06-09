package me.rhin.openciv.server.game.state;

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Json;

import me.rhin.openciv.server.Server;
import me.rhin.openciv.server.game.Game;
import me.rhin.openciv.server.game.Player;
import me.rhin.openciv.server.game.city.City;
import me.rhin.openciv.server.game.city.building.Building;
import me.rhin.openciv.server.game.city.building.type.Palace;
import me.rhin.openciv.server.game.city.specialist.SpecialistContainer;
import me.rhin.openciv.server.game.map.tile.Tile;
import me.rhin.openciv.server.game.map.tile.Tile.TileTypeWrapper;
import me.rhin.openciv.server.game.map.tile.TileType;
import me.rhin.openciv.server.game.map.tile.TileType.TileProperty;
import me.rhin.openciv.server.game.unit.Unit;
import me.rhin.openciv.server.game.unit.type.Settler.SettlerUnit;
import me.rhin.openciv.server.game.unit.type.Warrior.WarriorUnit;
import me.rhin.openciv.server.listener.ClickSpecialistListener;
import me.rhin.openciv.server.listener.ClickWorkedTileListener;
import me.rhin.openciv.server.listener.DisconnectListener;
import me.rhin.openciv.server.listener.EndTurnListener;
import me.rhin.openciv.server.listener.FetchPlayerListener;
import me.rhin.openciv.server.listener.NextTurnListener;
import me.rhin.openciv.server.listener.PlayerFinishLoadingListener;
import me.rhin.openciv.server.listener.PlayerListRequestListener;
import me.rhin.openciv.server.listener.SelectUnitListener;
import me.rhin.openciv.server.listener.SetProductionItemListener;
import me.rhin.openciv.server.listener.SettleCityListener;
import me.rhin.openciv.server.listener.UnitMoveListener;
import me.rhin.openciv.shared.packet.type.ClickSpecialistPacket;
import me.rhin.openciv.shared.packet.type.ClickWorkedTilePacket;
import me.rhin.openciv.shared.packet.type.DeleteUnitPacket;
import me.rhin.openciv.shared.packet.type.EndTurnPacket;
import me.rhin.openciv.shared.packet.type.FetchPlayerPacket;
import me.rhin.openciv.shared.packet.type.GameStartPacket;
import me.rhin.openciv.shared.packet.type.MoveUnitPacket;
import me.rhin.openciv.shared.packet.type.NextTurnPacket;
import me.rhin.openciv.shared.packet.type.PlayerDisconnectPacket;
import me.rhin.openciv.shared.packet.type.PlayerListRequestPacket;
import me.rhin.openciv.shared.packet.type.SelectUnitPacket;
import me.rhin.openciv.shared.packet.type.SetProductionItemPacket;
import me.rhin.openciv.shared.packet.type.SettleCityPacket;
import me.rhin.openciv.shared.packet.type.TerritoryGrowPacket;
import me.rhin.openciv.shared.packet.type.TurnTimeLeftPacket;

public class InGameState extends Game
		implements DisconnectListener, SelectUnitListener, UnitMoveListener, SettleCityListener,
		PlayerFinishLoadingListener, NextTurnListener, SetProductionItemListener, ClickWorkedTileListener,
		ClickSpecialistListener, EndTurnListener, PlayerListRequestListener, FetchPlayerListener {

	private static final int BASE_TURN_TIME = 9;

	private int currentTurn;
	private int turnTimeLeft;
	private ScheduledExecutorService executor;
	private Runnable turnTimeRunnable;

	public InGameState() {
		this.currentTurn = 0;
		this.turnTimeLeft = 0;

		this.executor = Executors.newScheduledThreadPool(1);

		this.turnTimeRunnable = new Runnable() {
			public void run() {
				try {
					if (!playersLoaded())
						return;
					if (turnTimeLeft <= 0) {
						Server.getInstance().getEventManager().fireEvent(new NextTurnEvent());
						currentTurn++;
						turnTimeLeft = getUpdatedTurnTime();
					}

					TurnTimeLeftPacket turnTimeLeftPacket = new TurnTimeLeftPacket();
					turnTimeLeftPacket.setTime(turnTimeLeft);

					Json json = new Json();
					for (Player player : players)
						player.getConn().send(json.toJson(turnTimeLeftPacket));

					turnTimeLeft--;

				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};
		executor.scheduleAtFixedRate(turnTimeRunnable, 0, 1, TimeUnit.SECONDS);

		loadGame();

		Server.getInstance().getEventManager().addListener(DisconnectListener.class, this);
		Server.getInstance().getEventManager().addListener(SelectUnitListener.class, this);
		Server.getInstance().getEventManager().addListener(UnitMoveListener.class, this);
		Server.getInstance().getEventManager().addListener(SettleCityListener.class, this);
		Server.getInstance().getEventManager().addListener(PlayerFinishLoadingListener.class, this);
		Server.getInstance().getEventManager().addListener(NextTurnListener.class, this);
		Server.getInstance().getEventManager().addListener(SetProductionItemListener.class, this);
		Server.getInstance().getEventManager().addListener(ClickWorkedTileListener.class, this);
		Server.getInstance().getEventManager().addListener(ClickSpecialistListener.class, this);
		Server.getInstance().getEventManager().addListener(EndTurnListener.class, this);
		Server.getInstance().getEventManager().addListener(PlayerListRequestListener.class, this);
		Server.getInstance().getEventManager().addListener(FetchPlayerListener.class, this);

	}

	@Override
	public void onStateEnd() {
		Server.getInstance().getEventManager().clearListenersFromObject(this);
	}

	@Override
	public void stop() {
		// Revert back to lobby state.
	}

	// TODO: On player connection, don't allow him to join yet. Since we haven't
	// implemented hot joining.

	@Override
	public void onDisconnect(WebSocket conn) {
		Player removedPlayer = getPlayerByConn(conn);

		if (removedPlayer == null)
			return;

		Server.getInstance().getEventManager().removeListener(NextTurnListener.class, removedPlayer);
		players.remove(removedPlayer);

		for (Player player : players) {
			WebSocket playerConn = player.getConn();

			PlayerDisconnectPacket packet = new PlayerDisconnectPacket();
			packet.setPlayerName(removedPlayer.getName());
			Json json = new Json();
			playerConn.send(json.toJson(packet));
		}

		if (players.size() < 1) {
			// TODO: Change State back to lobby. Or with popup window?
		}
	}

	@Override
	public void onUnitSelect(WebSocket conn, SelectUnitPacket packet) {

		// FIXME: Use a hashmap to get by unit name?
		Unit unit = map.getTiles()[packet.getGridX()][packet.getGridY()].getUnitFromID(packet.getUnitID());
		if (unit == null)
			return;
		Player player = getPlayerByConn(conn);
		if (!unit.getPlayerOwner().equals(player))
			return;

		player.setSelectedUnit(unit);
		unit.setSelected(true);

		Json json = new Json();
		conn.send(json.toJson(packet));
	}

	@Override
	public void onUnitMove(WebSocket conn, MoveUnitPacket packet) {

		Tile prevTile = map.getTiles()[packet.getPrevGridX()][packet.getPrevGridY()];

		Unit unit = prevTile.getUnitFromID(packet.getUnitID());

		if (unit == null) {
			System.out.println("Error: Unit is NULL");
			return;
		}

		Tile targetTile = map.getTiles()[packet.getTargetGridX()][packet.getTargetGridY()];

		unit.setTargetTile(targetTile);

		// The player is hacking here or i'm a poop coder
		if (unit.getMovement() < unit.getPathMovement())
			return;

		unit.moveToTargetTile();
		unit.getPlayerOwner().setSelectedUnit(null);
		unit.reduceMovement(packet.getMovementCost());

		packet.setMovementCost(unit.getPathMovement());

		Json json = new Json();
		for (Player player : players) {
			player.getConn().send(json.toJson(packet));
		}
	}

	@Override
	public void onSettleCity(WebSocket conn, SettleCityPacket settleCityPacket) {
		Player cityPlayer = getPlayerByConn(conn);
		settleCityPacket.setOwner(cityPlayer.getName());

		String cityName = "Unknown";
		boolean identicalName = true;

		while (identicalName) {
			identicalName = false;
			cityName = City.getRandomCityName();
			for (Player player : players) {
				for (City city : player.getOwnedCities()) {
					if (city.getName().equals(cityName))
						identicalName = true;
				}
			}
		}
		settleCityPacket.setCityName(cityName);

		Tile tile = map.getTiles()[settleCityPacket.getGridX()][settleCityPacket.getGridY()];
		Unit unit = null;

		for (Unit currentUnit : tile.getUnits())
			if (currentUnit instanceof SettlerUnit)
				unit = currentUnit;

		// The player is actually trying to hack if this is triggered
		if (unit == null)
			return;

		City city = new City(cityPlayer, cityName, tile);
		cityPlayer.addCity(city);
		cityPlayer.setSelectedUnit(null);

		DeleteUnitPacket deleteUnitPacket = new DeleteUnitPacket();
		deleteUnitPacket.setUnit(cityPlayer.getName(), unit.getID(), settleCityPacket.getGridX(),
				settleCityPacket.getGridY());

		Json json = new Json();
		for (Player player : players) {
			player.getConn().send(json.toJson(deleteUnitPacket));
			player.getConn().send(json.toJson(settleCityPacket));

			for (Tile territoryTile : city.getTerritory()) {
				if (territoryTile == null)
					continue;
				TerritoryGrowPacket territoryGrowPacket = new TerritoryGrowPacket();
				territoryGrowPacket.setCityName(city.getName());
				territoryGrowPacket.setLocation(territoryTile.getGridX(), territoryTile.getGridY());
				territoryGrowPacket.setOwner(city.getPlayerOwner().getName());
				player.getConn().send(json.toJson(territoryGrowPacket));
			}
		}

		city.addBuilding(new Palace(city));
		city.updateWorkedTiles();
	}

	@Override
	public void onPlayerFinishLoading(WebSocket conn) {
		getPlayerByConn(conn).finishLoading();
	}

	@Override
	public void onNextTurn() {
		Json json = new Json();
		NextTurnPacket turnTimeUpdatePacket = new NextTurnPacket();
		turnTimeUpdatePacket.setTurnTime(getUpdatedTurnTime());
		for (Player player : players) {
			player.getConn().send(json.toJson(turnTimeUpdatePacket));
		}
	}

	@Override
	public void onSetProductionItem(WebSocket conn, SetProductionItemPacket packet) {
		// Verify if the player owns that city.
		Player player = getPlayerByConn(conn);
		City targetCity = null;
		for (City city : player.getOwnedCities())
			if (city.getName().equals(packet.getCityName()))
				targetCity = city;

		// TODO: Verify if the item can be produced.

		if (targetCity == null)
			return;

		targetCity.getProducibleItemManager().setProducingItem(packet.getItemName());

		Json json = new Json();
		conn.send(json.toJson(packet));
	}

	@Override
	public void onClickWorkedTile(WebSocket conn, ClickWorkedTilePacket packet) {
		Player player = getPlayerByConn(conn);
		City targetCity = null;
		for (City city : player.getOwnedCities())
			if (city.getName().equals(packet.getCityName()))
				targetCity = city;

		if (targetCity == null)
			return;

		targetCity.clickWorkedTile(map.getTiles()[packet.getGridX()][packet.getGridY()]);
	}

	@Override
	public void onClickSpecialist(WebSocket conn, ClickSpecialistPacket packet) {
		// FIXME: This target city stuff is starting to seem redundant, lets fix that
		// soon.
		Player player = getPlayerByConn(conn);
		City targetCity = null;
		for (City city : player.getOwnedCities())
			if (city.getName().equals(packet.getCityName()))
				targetCity = city;

		if (targetCity == null)
			return;

		// FIXME: Should this be here or in the city class

		ArrayList<SpecialistContainer> specialistContainers = new ArrayList<>();
		specialistContainers.add(targetCity);

		for (Building building : targetCity.getBuildings())
			if (building instanceof SpecialistContainer)
				specialistContainers.add((SpecialistContainer) building);

		for (SpecialistContainer container : specialistContainers)
			if (container.getName().equals(packet.getContainerName()))
				targetCity.removeSpecialistFromContainer(container);
	}

	@Override
	public void onEndTurn(WebSocket conn, EndTurnPacket packet) {
		Server.getInstance().getEventManager().fireEvent(new NextTurnEvent());
		currentTurn++;
		turnTimeLeft = getUpdatedTurnTime();

		TurnTimeLeftPacket turnTimeLeftPacket = new TurnTimeLeftPacket();
		turnTimeLeftPacket.setTime(turnTimeLeft);

		Json json = new Json();
		for (Player player : players)
			player.getConn().send(json.toJson(turnTimeLeftPacket));
	}

	@Override
	public void onPlayerListRequested(WebSocket conn, PlayerListRequestPacket packet) {
		System.out.println("[SERVER] Player list requested");
		for (Player player : players) {
			packet.addPlayer(player.getName(), player.getCivType().name());
		}
		Json json = new Json();
		conn.send(json.toJson(packet));
	}

	@Override
	public void onPlayerFetch(WebSocket conn, FetchPlayerPacket packet) {
		System.out.println("[SERVER] Fetching player...");
		Player player = getPlayerByConn(conn);
		packet.setPlayerName(player.getName());
		Json json = new Json();
		conn.send(json.toJson(packet));
	}

	@Override
	public String toString() {
		return "InGame";
	}

	private boolean playersLoaded() {
		for (Player player : players)
			if (!player.isLoaded())
				return false;

		return true;
	}

	private int getMaxPlayerCities() {
		int maxCities = 0;
		for (Player player : players)
			if (player.getOwnedCities().size() > maxCities)
				maxCities = player.getOwnedCities().size();
		return maxCities;
	}

	private int getMaxPlayerUnits() {
		int maxUnits = 0;
		for (Player player : players)
			if (player.getOwnedUnits().size() > maxUnits)
				maxUnits = player.getOwnedUnits().size();
		return maxUnits;
	}

	private int getUpdatedTurnTime() {
		int cityMultiplier = 1;
		int unitMultiplier = 1;
		return BASE_TURN_TIME + getMaxPlayerCities() * cityMultiplier + getMaxPlayerUnits() * unitMultiplier;
	}

	private int getTurnTimeLeft() {
		return turnTimeLeft;
	}

	private void loadGame() {
		System.out.println("[SERVER] Starting game...");
		map.generateTerrain();

		// Start the game
		Json json = new Json();
		GameStartPacket gameStartPacket = new GameStartPacket();
		for (Player player : players) {
			player.getConn().send(json.toJson(gameStartPacket));
		}

		Random rnd = new Random();

		int iterations = 0;
		for (int i = 0; i < players.size(); i++) {
			Player player = players.get(i);
			Rectangle rect = map.getMapPartition().get(i);

			int rndX = -1;
			int rndY = -1;

			// FIXME: Use the tile indexer to locate habitable tiles
			while (true) {
				iterations++;

				float padding = 0.25F;
				int minX = (int) (rect.getX() + (rect.getWidth() * padding));
				int minY = (int) (rect.getY() + (rect.getHeight() * padding));
				int maxX = (int) (rect.getX() + rect.getWidth() - (rect.getWidth() * padding));
				int maxY = (int) (rect.getY() + rect.getHeight() - (rect.getWidth() * padding));
				rndX = rnd.nextInt(maxX - minX + 1) + minX;
				rndY = rnd.nextInt(maxY - minY + 1) + minY;
				Tile tile = map.getTiles()[rndX][rndY];

				if (tile.containsTileType(TileType.OCEAN) || tile.containsTileType(TileType.MOUNTAIN)
						|| tile.containsTileType(TileType.TUNDRA) || !tile.hasRivers()
						|| tile.containsTileType(TileType.DESERT_HILL) || tile.containsTileType(TileType.DESERT))
					continue;

				// Check if there is room for 2 units.
				boolean hasSafeTile = false;
				for (Tile adjTile : tile.getAdjTiles())
					if (!adjTile.containsTileType(TileType.OCEAN) && !adjTile.containsTileType(TileType.MOUNTAIN))
						hasSafeTile = true;

				if (hasSafeTile) {
					player.setSpawnPos(rndX, rndY);
					break;
				}

			}
		}

		// Spawn in the players at fair locations

		for (Player player : players) {
			Tile tile = map.getTiles()[player.getSpawnX()][player.getSpawnY()];
			tile.addUnit(new SettlerUnit(player, tile));

			for (Tile adjTile : tile.getAdjTiles()) {
				if (!adjTile.getBaseTileType().hasProperty(TileProperty.WATER)
						&& !adjTile.containsTileType(TileType.MOUNTAIN)) {
					adjTile.addUnit(new WarriorUnit(player, adjTile));
					break;
				}
			}
		}

		// Add two luxuries around the player
		for (Player player : players) {
			int assignedLuxTiles = 0;
			int assignedResourceTiles = 0;
			int loopLimit = 500;
			while ((assignedLuxTiles < 3 || assignedResourceTiles < 2) && loopLimit > 0) {

				int randX = rnd.nextInt(7) - 3;
				int randY = rnd.nextInt(7) - 3;
				Tile tile = map.getTiles()[player.getSpawnX() + randX][player.getSpawnY() + randY];

				if (tile.getBaseTileType().hasProperty(TileProperty.WATER) || tile.getBaseTileType() == TileType.DESERT
						|| tile.getBaseTileType() == TileType.DESERT_HILL
						|| tile.getBaseTileType() == TileType.MOUNTAIN) {
					continue;
				}

				if (assignedLuxTiles < 3) {
					tile.setTileType(TileType.getRandomLandLuxuryTile());
					assignedLuxTiles++;
				} else {

					for (TileTypeWrapper tileWrapper : tile.getTileTypeWrappers())
						if (tileWrapper.getTileType().hasProperty(TileProperty.LUXURY))
							continue;

					tile.setTileType(TileType.getRandomResourceTile());
					assignedResourceTiles++;
				}

				loopLimit--;
			}
		}
	}
}