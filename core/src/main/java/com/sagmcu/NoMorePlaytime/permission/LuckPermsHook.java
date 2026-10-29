package com.sagmcu.NoMorePlaytime.permission;

import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;

import java.util.UUID;

/** The only class that touches the LuckPerms API; it is never loaded when LuckPerms is not installed. */
final class LuckPermsHook {
	private LuckPermsHook() {
	}

	static Boolean query(UUID player, String node) {
		User user = LuckPermsProvider.get().getUserManager().getUser(player);
		if (user == null)
			return null;
		Tristate result = user.getCachedData().getPermissionData().checkPermission(node);
		return result == Tristate.UNDEFINED ? null : Boolean.valueOf(result.asBoolean());
	}
}
