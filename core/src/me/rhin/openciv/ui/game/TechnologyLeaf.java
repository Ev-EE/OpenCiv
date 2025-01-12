package me.rhin.openciv.ui.game;

import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;

import me.rhin.openciv.Civilization;
import me.rhin.openciv.asset.TextureEnum;
import me.rhin.openciv.game.research.Technology;
import me.rhin.openciv.ui.background.ColoredBackground;
import me.rhin.openciv.ui.label.CustomLabel;
import me.rhin.openciv.ui.window.type.PickResearchWindow;

public class TechnologyLeaf extends Group {

	private Technology tech;
	private ColoredBackground background;
	private ColoredBackground icon;
	private CustomLabel techNameLabel;
	private Vector2 backVector;
	private Vector2 frontVector;

	public TechnologyLeaf(final Technology tech, float x, float y, float width, float height) {
		this.tech = tech;
		this.setTouchable(Touchable.enabled);
		this.setBounds(x, y, width, height);

		Sprite sprite = null;

		if (tech.isResearched())
			sprite = TextureEnum.UI_GREEN.sprite();
		else if (tech.hasResearchedRequiredTechs())
			sprite = TextureEnum.UI_YELLOW.sprite();
		else
			sprite = TextureEnum.UI_RED.sprite();

		this.background = new ColoredBackground(sprite, 0, 0, width, height);
		addActor(background);

		this.icon = new ColoredBackground(tech.getIcon(), width / 2 - 32 / 2, 4, 32, 32);
		addActor(icon);

		// FIXME: Setting the label to a height > 0 causes click input isses.
		this.techNameLabel = new CustomLabel(tech.getName(), Align.center, 0, height - 20, width, 0);
		addActor(techNameLabel);

		this.backVector = new Vector2(x, y + height / 2);
		this.frontVector = new Vector2(x + width, y + height / 2);

		addListener(new ClickListener() {

			@Override
			public void clicked(InputEvent event, float x, float y) {
				if (!Civilization.getInstance().getWindowManager().allowsInput(event.getListenerActor())
						|| !tech.hasResearchedRequiredTechs()) {
					return;
				}

				Civilization.getInstance().getWindowManager().addWindow(new PickResearchWindow(tech));
			}

			@Override
			public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
			}

			@Override
			public void exit(InputEvent event, float x, float y, int pointer, Actor fromActor) {
			}
		});
	}

	@Override
	public void setPosition(float x, float y) {
		super.setPosition(x, y);

		backVector.set(x, y + getHeight() / 2);
		frontVector.set(x + getWidth(), y + getHeight() / 2);
	}

	public Technology getTech() {
		return tech;
	}

	public void onClicked() {
		// Start researching the tech, ect.
		System.out.println("Research: " + tech.getName());
	}

	public Vector2 getBackVector() {
		return backVector;
	}

	public Vector2 getFrontVector() {
		return frontVector;
	}

}
