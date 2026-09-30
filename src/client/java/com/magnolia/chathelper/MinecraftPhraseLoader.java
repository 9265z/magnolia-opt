package com.magnolia.chathelper;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

final class MinecraftPhraseLoader {
	private MinecraftPhraseLoader() {
	}

	static void addRegistryPhrases(UnreverseResolver resolver) {
		addRegistry(resolver, BuiltInRegistries.ITEM);
		addRegistry(resolver, BuiltInRegistries.BLOCK);
	}

	private static void addRegistry(UnreverseResolver resolver, Registry<?> registry) {
		for (Identifier id : registry.keySet()) {
			resolver.addPhrase(id.getPath().replace('_', ' '));
		}
	}
}
