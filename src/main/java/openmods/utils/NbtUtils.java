package openmods.utils;

import com.google.common.base.MoreObjects;
import java.util.UUID;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.common.util.Constants;

public class NbtUtils {

	private static final String KEY = "K";
	private static final String VALUE = "V";

	private static final String TAG_Z = "Z";
	private static final String TAG_Y = "Y";
	private static final String TAG_X = "X";

	public static boolean hasCoordinates(CompoundNBT tag) {
		return tag.contains(TAG_X, Constants.NBT.TAG_ANY_NUMERIC) &&
				tag.contains(TAG_Y, Constants.NBT.TAG_ANY_NUMERIC) &&
				tag.contains(TAG_Z, Constants.NBT.TAG_ANY_NUMERIC);
	}

	public static CompoundNBT store(CompoundNBT tag, int x, int y, int z) {
		tag.putInt(TAG_X, x);
		tag.putInt(TAG_Y, y);
		tag.putInt(TAG_Z, z);
		return tag;
	}

	public static CompoundNBT store(int x, int y, int z) {
		return store(new CompoundNBT(), x, y, z);
	}

	public static CompoundNBT store(CompoundNBT tag, double x, double y, double z) {
		tag.putDouble(TAG_X, x);
		tag.putDouble(TAG_Y, y);
		tag.putDouble(TAG_Z, z);
		return tag;
	}

	public static CompoundNBT store(double x, double y, double z) {
		return store(new CompoundNBT(), x, y, z);
	}

	public static CompoundNBT store(CompoundNBT tag, Vec3i coords) {
		return store(tag, coords.getX(), coords.getY(), coords.getZ());
	}

	public static CompoundNBT store(Vec3i coords) {
		return store(new CompoundNBT(), coords.getX(), coords.getY(), coords.getZ());
	}

	public static CompoundNBT store(CompoundNBT tag, Coord coords) {
		return store(tag, coords.x, coords.y, coords.z);
	}

	public static CompoundNBT store(Coord coords) {
		return store(new CompoundNBT(), coords.x, coords.y, coords.z);
	}

	public static CompoundNBT store(CompoundNBT tag, BlockPos coords) {
		return store(tag, coords.getX(), coords.getY(), coords.getZ());
	}

	public static CompoundNBT store(BlockPos coords) {
		return store(new CompoundNBT(), coords.getX(), coords.getY(), coords.getZ());
	}

	public static CompoundNBT store(CompoundNBT tag, UUID uuid) {
		tag.putLong("UUIDMost", uuid.getMostSignificantBits());
		tag.putLong("UUIDLeast", uuid.getLeastSignificantBits());
		return tag;
	}

	public static CompoundNBT store(UUID uuid) {
		return store(new CompoundNBT(), uuid);
	}

	public static CompoundNBT store(Vec3d vec) {
		return store(vec.x, vec.y, vec.z);
	}

	public static CompoundNBT store(CompoundNBT tag, ResourceLocation location) {
		tag.putString(KEY, location.getNamespace());
		tag.putString(VALUE, location.getPath());
		return tag;
	}

	public static CompoundNBT store(ResourceLocation location) {
		return store(new CompoundNBT(), location);
	}

	public static Coord readCoord(CompoundNBT tag) {
		final int x = tag.getInt(TAG_X);
		final int y = tag.getInt(TAG_Y);
		final int z = tag.getInt(TAG_Z);
		return new Coord(x, y, z);
	}

	public static BlockPos readBlockPos(CompoundNBT tag) {
		final int x = tag.getInt(TAG_X);
		final int y = tag.getInt(TAG_Y);
		final int z = tag.getInt(TAG_Z);
		return new BlockPos(x, y, z);
	}

	public static Vec3d readVec(CompoundNBT tag) {
		final double x = tag.getDouble(TAG_X);
		final double y = tag.getDouble(TAG_Y);
		final double z = tag.getDouble(TAG_Z);
		return new Vec3d(x, y, z);
	}

	public static UUID readUuid(CompoundNBT tag) {
		final long most = tag.getLong("UUIDMost");
		final long least = tag.getLong("UUIDLeast");
		return new UUID(most, least);
	}

	public static ResourceLocation readResourceLocation(final CompoundNBT entry) {
		final String domain = entry.getString(KEY);
		final String path = entry.getString(VALUE);
		final ResourceLocation blockLocation = new ResourceLocation(domain, path);
		return blockLocation;
	}

	public static <T extends Enum<T>> T readEnum(CompoundNBT tag, String name, Class<T> cls) {
		if (tag.contains(name, Constants.NBT.TAG_ANY_NUMERIC)) {
			int ordinal = tag.getInt(name);
			return EnumUtils.fromOrdinal(cls, ordinal);
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	public static <T extends Enum<T>> T readEnum(CompoundNBT tag, String name, T defaultValue) {
		return MoreObjects.firstNonNull(readEnum(tag, name, (Class<T>)defaultValue.getClass()), defaultValue);
	}
}
