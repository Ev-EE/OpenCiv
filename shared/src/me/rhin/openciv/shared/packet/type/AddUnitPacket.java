package me.rhin.openciv.shared.packet.type;

import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonValue;

import me.rhin.openciv.shared.packet.Packet;

public class AddUnitPacket extends Packet {

	int tileGridX, tileGridY;
	private String unitName;
	private String playerOwner;

	public AddUnitPacket() {
		super(AddUnitPacket.class.getName());
	}

	@Override
	public void write(Json json) {
		super.write(json);
		json.writeValue("tileGridX", tileGridX);
		json.writeValue("tileGridY", tileGridY);
		json.writeValue("unitName", unitName);
		json.writeValue("playerOwner", playerOwner);
	}

	@Override
	public void read(Json json, JsonValue jsonData) {
		super.read(json, jsonData);
		this.tileGridX = jsonData.getInt("tileGridX");
		this.tileGridY = jsonData.getInt("tileGridY");
		this.unitName = jsonData.getString("unitName");
		this.playerOwner = jsonData.getString("playerOwner");
	}

	public void setUnit(String playerOwner, String unitName, int tileGridX, int tileGridY) {
		this.tileGridX = tileGridX;
		this.tileGridY = tileGridY;
		this.unitName = unitName;
		this.playerOwner = playerOwner;
	}

	public int getTileGridX() {
		return tileGridX;
	}

	public int getTileGridY() {
		return tileGridY;
	}

	public String getUnitName() {
		return unitName;
	}

	public String getPlayerOwner() {
		return playerOwner;
	}
}