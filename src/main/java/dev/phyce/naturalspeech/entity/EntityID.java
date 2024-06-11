package dev.phyce.naturalspeech.entity;

import com.google.common.base.Preconditions;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.JsonAdapter;
import dev.phyce.naturalspeech.statics.Names;
import dev.phyce.naturalspeech.utils.Standardize;
import java.lang.reflect.Type;
import java.util.Objects;
import javax.annotation.Nullable;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Friend;
import net.runelite.api.NPC;
import net.runelite.api.Player;

@Slf4j
@JsonAdapter(EntityID.JSONAdaptor.class)
public final class EntityID {
	public static final EntityID USER = name(Names.USER);
	public static final EntityID SYSTEM = name(Names.SYSTEM);
	public static final EntityID GLOBAL_NPC = name(Names.GLOBAL_NPC);
	/**
	 * The JSON schema version
	 */
	static final int VERSION = 1;

	final Integer id;
	final String name;

	private EntityID(@Nullable Integer id, @Nullable String name) {
		this.id = id;
		this.name = name;
	}

	public static EntityID name(@NonNull String name) {
		return new EntityID(null, Standardize.standardName(name));
	}

	public static EntityID id(int id) {
		return new EntityID(id, null);
	}

	public static EntityID npc(@NonNull NPC npc) {
		return new EntityID(Standardize.standardNpcID(npc), null);
	}

	public static EntityID player(@NonNull Player player) {
		Preconditions.checkNotNull(player.getName());

		return new EntityID(null, Standardize.standardPlayerName(player));
	}

	public static EntityID friend(@NonNull Friend friend) {
		Preconditions.checkNotNull(friend.getName());

		return new EntityID(null, Standardize.standardFriendName(friend));
	}

	@Override
	public boolean equals(Object other) {
		if (other == null) return false;
		if (!(other instanceof EntityID)) return false;

		EntityID otherEntity = (EntityID) other;

		if (this.id != null && otherEntity.id != null) {
			return this.id.equals(otherEntity.id);
		}

		if (this.name != null && otherEntity.name != null) {
			return this.name.equals(otherEntity.name);
		}

		return false;
	}

	public boolean isUser() {
		return this.equals(USER);
	}

	public boolean isNPC(@NonNull NPC npc) {
		return this.equals(EntityID.npc(npc));
	}

	public boolean isPlayer(@NonNull Player player) {
		return this.equals(EntityID.player(player));
	}

	public boolean isFriend(@NonNull Friend friend) {
		return this.equals(EntityID.friend(friend));
	}

	public boolean isName(@NonNull String name) {
		return Objects.equals(this.name, Standardize.standardName(name));
	}

	public boolean isValid() {
		return id != null || name != null;
	}

	@Override
	public int hashCode() {
		if (id != null) {
			return id.hashCode();
		}
		else if (name != null) {
			return name.hashCode();
		}
		else {
			log.error("SID has no id or name during hashCode()");
			return Objects.hashCode(null);
		}
	}

	@NonNull
	@Override
	public String toString() {
		if (id != null && name != null) return "EntityID(INVALID, id=" + id + ", name=" + name + ")";
		else if (id != null) return "EntityID(id=" + id + ")";
		else if (name != null) return "EntityID(standardName=" + name + ")";
		else return "EntityID(null)";
	}

	public String toShortString() {
		if (id != null && name != null) return "EntityID(INVALID, id=" + id + ", name=" + name + ")";
		else if (id != null) return "NPC" + id;
		else if (name != null) return name;
		else return "EntityID(null)";
	}

	static class JSONAdaptor implements JsonSerializer<EntityID>, JsonDeserializer<EntityID> {

		@Override
		public JsonElement serialize(EntityID src, Type typeOfSrc, JsonSerializationContext context) {
			JsonObject json = new JsonObject();
			json.add("version", context.serialize(VERSION));

			if (src.id != null) json.add("id", context.serialize(src.id));
			else if (src.name != null) json.add("name", context.serialize(src.name));
			else throw new IllegalStateException("SID must have either id or name");

			return json;
		}

		@Override
		public EntityID deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
			throws JsonParseException {
			if (!json.isJsonObject()) throw new JsonParseException("Expected JsonObject, got " + json);

			JsonObject jsonObject = json.getAsJsonObject();
			int version = context.deserialize(jsonObject.get("version"), Integer.class);
			if (version != VERSION) throw new JsonParseException("Expected version " + VERSION + ", got " + version);

			Integer id = null;
			String name = null;
			if (jsonObject.has("id")) id = context.deserialize(jsonObject.get("id"), Integer.class);
			if (jsonObject.has("name")) name = context.deserialize(jsonObject.get("name"), String.class);

			if (id == null && name == null) {
				log.error("SID must have either id or name");
				return null;
			}
			else {
				return new EntityID(id, name);
			}
		}
	}
}
