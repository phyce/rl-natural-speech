package dev.phyce.naturalspeech.utils;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import dev.phyce.naturalspeech.statics.Names;
import java.lang.reflect.Type;
import java.util.Objects;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Friend;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.util.Text;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Standardize this, standardize that, standardize everything! All in one package!
 */
@Slf4j
public final class Standardize {
	public static final SID LOCAL_PLAYER_SID = new SID(Names.LOCAL_USER);
	public static final SID SYSTEM_SID = new SID(Names.SYSTEM);

	@NonNull
	public static String name(@NonNull String name) {
		return Texts.removeLevelFromTargetName(Text.standardize(Text.removeTags(name)));
	}

	@NonNull
	public static String name(@NonNull Actor actor) {
		return name(Objects.requireNonNull(actor.getName()));
	}

	@NonNull
	public static String name(@NonNull ChatMessage message) {
		return Objects.requireNonNull(name(message.getName()));
	}

	@NonNull
	public static String name(@NonNull NPC actor) {
		NPCComposition composition = actor.getTransformedComposition();
		if (composition != null) {
			return name(composition.getName());
		}
		else {
			return name(actor);
		}
	}

	public static int id(@NonNull NPC actor) {
		NPCComposition composition = actor.getTransformedComposition();
		if (composition != null) {
			return composition.getId();
		}
		else {
			return actor.getId();
		}
	}

	public static boolean equals(@Nullable Player a, @Nullable SID sid) {
		if (a == null || sid == null) {
			return false;
		}
		else {
			return sid.equals(a);
		}
	}

	public static boolean equals(@Nullable SID sid, @Nullable Player a) {
		return equals(a, sid);
	}

	public static boolean equals(@Nullable SID a, @Nullable SID b) {
		if (a == null && b == null) {
			return true;
		}
		else if (a == null || b == null) {
			return false;
		}
		else {
			return a.equals(b);
		}
	}

	public static boolean equals(@Nullable Actor a, @Nullable Actor b) {
		if (a == null && b == null) {
			return true;
		}
		else if (a == null || b == null) {
			return false;
		}
		else {
			return equals(a.getName(), b.getName());
		}
	}

	public static boolean equals(@Nullable String name, @Nullable Actor actor) {
		if (actor == null && name == null) {
			return true;
		}
		else if (actor == null || name == null) {
			return false;
		}
		else {
			return equals(actor.getName(), name);
		}
	}

	public static boolean equals(@Nullable Actor actor, @Nullable String name) {
		return equals(name, actor);
	}

	public static boolean equals(@Nullable String nameA, @Nullable String nameB) {
		// we cannot standardize null strings
		if (nameA == null && nameB == null) {
			return true;
		}
		else if (nameA == null || nameB == null) {
			return false;
		}
		else {
			return Text.standardize(nameA).equals(Text.standardize(nameB));
		}
	}

	public static boolean equals(@NonNull Friend friend, @NonNull SID sid) {
		return sid.equals(friend.getName());
	}

	public static boolean equals(@NonNull SID sid, @NonNull Friend friend) {
		return equals(friend, sid);
	}

	@JsonAdapter(SIDJSONAdaptor.class)
	public static class SID {
		static final int VERSION = 1;

		final Integer id;
		final String name;

		SID(Integer id, String name) {
			this.id = id;
			this.name = name;
		}

		public SID(@NonNull String name) {
			this.id = null;
			this.name = Standardize.name(name);
		}

		public SID(int id) {
			this.id = id;
			this.name = null;
		}

		public SID(@NonNull NPC actor) {
			this.id = Standardize.id(actor);
			this.name = Standardize.name(actor);
		}

		public SID(@NonNull Player player) {
			this.id = null;
			this.name = Standardize.name(player);
		}

		public SID(@NonNull Friend friend) {
			this.id = null;
			this.name = Standardize.name(friend.getName());
		}

		public boolean equals(SID other) {
			if (id != null && other.id != null) {
				return id.equals(other.id);
			}
			else if (name != null && other.name != null) {
				return name.equals(other.name);
			}
			else {
				return false;
			}
		}

		public boolean equals(NPC actor) {
			return Standardize.id(actor) == id;
		}

		public boolean equals(Player player) {
			return Standardize.equals(player, name);
		}

		public boolean equals(Friend friend) {
			return Standardize.equals(friend.getName(), name);
		}

		public boolean equals(String name) {
			return Standardize.equals(name, this.name);
		}

		@NonNull
		@Override
		public String toString() {
			if (id != null) {
				return "SID(id=" + id + ")";
			}
			else {
				return "SID(standardName=" + name + ")";
			}
		}
	}

	private static class SIDJSONAdaptor implements JsonSerializer<SID>, JsonDeserializer<SID> {

		@Override
		public JsonElement serialize(SID src, Type typeOfSrc, JsonSerializationContext context) {
			JsonObject json = new JsonObject();
			json.add("version", context.serialize(SID.VERSION));
			if (src.id != null) {
				json.add("id", context.serialize(src.id));
			}
			else if (src.name != null) {
				json.add("name", context.serialize(src.name));
			}
			else {
				throw new IllegalStateException("SID must have either id or name");
			}

			return null;
		}

		@Override
		public SID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
			throws JsonParseException {
			if (!json.isJsonObject()) {
				throw new JsonParseException("Expected JsonObject, got " + json);
			}

			JsonObject jsonObject = json.getAsJsonObject();
			int version = context.deserialize(jsonObject.get("version"), Integer.class);
			if (version != SID.VERSION) {
				throw new JsonParseException("Expected version " + SID.VERSION + ", got " + version);
			}


			Integer id = null;
			String name = null;
			if (jsonObject.has("id")) {
				id = context.deserialize(jsonObject.get("id"), Integer.class);
			}
			if (jsonObject.has("name")) {
				name = context.deserialize(jsonObject.get("name"), String.class);
			}

			if (id == null && name == null) {
				log.error("SID must have either id or name");
				return null;
			} else {
				return new SID(id, name);
			}

		}
	}
}
