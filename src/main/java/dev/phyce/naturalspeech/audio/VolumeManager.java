package dev.phyce.naturalspeech.audio;

import com.google.common.base.Optional;
import com.google.inject.Inject;
import dev.phyce.naturalspeech.NaturalSpeechConfig;
import dev.phyce.naturalspeech.PluginModule;
import dev.phyce.naturalspeech.entity.EntityID;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import dev.phyce.naturalspeech.utils.ChatHelper;
import dev.phyce.naturalspeech.utils.ClientHelper;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.client.eventbus.Subscribe;

@Slf4j
@PluginSingleton
public class VolumeManager implements PluginModule {

	public final static Supplier<Float> ZERO_GAIN = () -> 0f;
	public static final float NOISE_FLOOR = -60f;
	public static final float CHAT_FLOOR = -50f;
	public static final float FRIEND_FLOOR = -20f;
	public static final float NPC_FLOOR = -50f;

	public static final float CHAT_MAX_DISTANCE = 15f;
	public static final float NPC_MAX_DISTANCE = 15f;

	private final Client client;
	private final ClientHelper clientHelper;
	private final NaturalSpeechConfig config;
	private final Set<Actor> spawnedActors = new HashSet<>();


	@Inject
	public VolumeManager(
		Client client,
		ClientHelper clientHelper,
		NaturalSpeechConfig config
	) {
		this.client = client;
		this.clientHelper = clientHelper;
		this.config = config;

		WorldView topLevelWorldView = client.getTopLevelWorldView();
		if (topLevelWorldView != null) {
			spawnedActors.addAll(topLevelWorldView.npcs().stream().collect(Collectors.toList()));
			spawnedActors.addAll(topLevelWorldView.players().stream().collect(Collectors.toList()));
		}

	}

	@NonNull
	public Supplier<Float> overhead(Actor actor) {
		return () -> {
			if (!config.distanceFadeEnabled()) return 0f;

			if (this.spawnedActors.contains(actor)) {
				WorldPoint sourceLocation = actor.getWorldLocation();
				WorldPoint listenerLocation = client.getLocalPlayer().getWorldLocation();

				float distance = distance(listenerLocation, sourceLocation);
				return Math.max(CHAT_FLOOR, attenuation(distance, CHAT_MAX_DISTANCE, CHAT_FLOOR));
			}
			else return NOISE_FLOOR; // actor has despawned, silence
		};
	}

	public Supplier<Float> npc(NPC npc) {
		return () -> {
			if (!config.distanceFadeEnabled()) return 0f;

			if (this.spawnedActors.contains(npc)) {
				WorldPoint sourceLocation = npc.getWorldLocation();
				WorldPoint listenerLocation = client.getLocalPlayer().getWorldLocation();

				float distance = distance(listenerLocation, sourceLocation);
				return Math.max(NPC_FLOOR, attenuation(distance, NPC_MAX_DISTANCE, NPC_FLOOR));
			}
			else return NOISE_FLOOR; // actor has despawned, silence
		};
	}

	public Supplier<Float> friend(Player player) {
		return () -> {
			if (!config.distanceFadeEnabled()) return 0f;

			if (this.spawnedActors.contains(player)) {
				WorldPoint sourceLoc = player.getWorldLocation();
				WorldPoint listenLoc = client.getLocalPlayer().getWorldLocation();

				float distance = distance(sourceLoc, listenLoc);
				return Math.max(FRIEND_FLOOR, attenuation(distance, CHAT_MAX_DISTANCE, FRIEND_FLOOR));
			}
			else return NOISE_FLOOR; // actor has despawned, silence
		};
	}

	@NonNull
	public Supplier<Float> dialog() {
		return ZERO_GAIN;
	}

	@NonNull
	public Supplier<Float> system() {
		return ZERO_GAIN;
	}


	public Supplier<Float> localplayer() {
		return ZERO_GAIN;
	}

	public Supplier<Float> chat(ChatHelper.ChatType chatType, EntityID entityID) {
		Supplier<Float> volume;
		switch (chatType) {
			case User:
				volume = localplayer();
				break;
			case OtherPlayers:
				Optional<Player> player = clientHelper.getPlayer(entityID);

				if (!player.isPresent()) {
					volume = localplayer();
				}
				else if (clientHelper.isFriend(entityID)) {
					volume = friend(player.get());
				}
				else {
					volume = overhead(player.get());
				}
				break;
			case System:
				volume = system();
				break;
			default:
				throw new RuntimeException("Unknown ChatVoiceType");
		}
		return volume;
	}

	public float attenuation(float distance, float maxDistance, float floor) {
		if (distance < 1) return 0;

		//noinspection UnnecessaryLocalVariable
		float result = floor * easeInOutQuad(distance / maxDistance);

		return result;
	}
	// we need accurate distances, WorldPoint::distanceTo is too coarse for audio

	private static float distance(WorldPoint a, WorldPoint b) {
		int distanceX = a.getX() - b.getX();
		int distanceY = a.getY() - b.getY();
		int distanceZ = a.getPlane() - b.getPlane();
		return (float) Math.sqrt(distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ);
	}
	// https://easings.net/#easeInOutQuad

	private static float easeInOutQuad(float x) {
		return (float) (x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2);
	}

	@Subscribe
	private void onPlayerSpawned(PlayerSpawned event) {
		spawnedActors.add(event.getActor());
	}

	@Subscribe
	private void onPlayerDespawned(PlayerDespawned event) {
		spawnedActors.remove(event.getActor());
	}

	@Subscribe
	private void onNpcSpawned(NpcSpawned event) {
		spawnedActors.add(event.getActor());
	}

	@Subscribe
	private void onNpcDespawned(NpcDespawned event) {
		spawnedActors.remove(event.getActor());
	}

	public static float volumeToGain(int volume100) {
		// range[-80, 0]
		float gainDB;

		// Graph of the function
		// https://www.desmos.com/calculator/wdhsfbxgeo

		// clamp to 0-100
		float volume = Math.min(100, volume100);
		// convert linear volume 0-100 to log control
		if (volume <= 0.1) gainDB = NOISE_FLOOR;
		else gainDB = (float) (10 * (Math.log(volume / 100)));

		log.info("returning volume {} -> {}db", volume100, gainDB);
		return gainDB;
	}
}
