package dev.phyce.naturalspeech.eventbus;

import com.google.common.base.Preconditions;
import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Iterables;
import com.google.inject.ProvisionException;
import com.google.inject.spi.Message;
import dev.phyce.naturalspeech.singleton.PluginSingleton;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Synchronized;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.EventBus;
import org.slf4j.Marker;
import org.slf4j.MarkerFactory;

// Private event bus inside Plugin scope
// This private eventbus is entirely separate from RuneLites' PluginEventBus
// Regardless of internal plugin registers, the entire PluginEventBus will be GC-ed on plugin shutdown

// This implementation automatically unregisters weak subscribers when they are garbage collected

/**
 * <p><b>No need to call {@link EventBus#unregister(Object)}.</b></p>
 * <p>The subscriber object will be weakly referenced,
 * and will be automatically unregistered when garbage collected.</p>
 * <p>Weak Handler methods must be public, we can't use private reflection.</p>
 */
@Slf4j
@RequiredArgsConstructor
@PluginSingleton
public class PluginEventBus {
	private static final Marker DEDUPLICATE = MarkerFactory.getMarker("DEDUPLICATE");

	@Value
	public static class WeakSubscriber {
		WeakReference<Object> objectWeak;
		Class<?> objectClass;
		Method method;
		float priority;

		public WeakSubscriber(final Object object, final Method method, final float priority) {
			this.objectWeak = new WeakReference<>(object);
			this.objectClass = object.getClass();
			this.method = method;
			this.priority = priority;
		}

		void invoke(final Object arg) throws Exception {
			Object object = objectWeak.get();
			if (object != null) method.invoke(object, arg);
		}
	}

	private final Consumer<Throwable> exceptionHandler;

	@Nonnull
	private ImmutableMultimap<Class<?>, WeakSubscriber> weakSubscribers = ImmutableMultimap.of();

	public PluginEventBus() {
		this((e) -> log.warn(DEDUPLICATE, "Uncaught exception in event subscriber", e));
	}

	@Synchronized
	public void registerWeak(@NonNull final Object object) {

		Preconditions.checkState(weakSubscribers.values().stream()
						.noneMatch(sub -> Objects.equals(sub.getObjectWeak().get(), object)),
				"Registering object already registered:%s", object);

		final ImmutableMultimap.Builder<Class<?>, WeakSubscriber> builder = ImmutableMultimap.builder();

		weakSubscribers.forEach((k, v) -> {
			if (v.objectWeak.get() != null) builder.put(k, v);
		});

		builder.orderValuesBy(Comparator.comparingDouble(WeakSubscriber::getPriority).reversed()
				.thenComparing(s -> s.objectClass.getName()));


		for (Class<?> clazz = object.getClass(); clazz != null; clazz = clazz.getSuperclass()) {
			for (final Method method : clazz.getDeclaredMethods()) {
				final PluginSubscribe sub = method.getAnnotation(PluginSubscribe.class);

				if (sub == null) {
					continue;
				}

				checkArgument(method.getReturnType() == Void.TYPE,
						"@Subscribed method \"" + method + "\" cannot return a value");
				checkArgument(method.getParameterCount() == 1,
						"@Subscribed method \"" + method + "\" must take exactly 1 argument");
				checkArgument(!Modifier.isStatic(method.getModifiers()),
						"@Subscribed method \"" + method + "\" cannot be static");
				// RuneLite Plugin Rules: no illegal private reflection restriction
				checkArgument(Modifier.isPublic(method.getModifiers()),
						"@Subscribed method \"" + method + "\" must be public");


				final Class<?> parameterClazz = method.getParameterTypes()[0];

				checkArgument(!parameterClazz.isPrimitive(),
						"@Subscribed method \"" + method + "\" cannot subscribe to primitives");
				checkArgument(
						(parameterClazz.getModifiers() & (Modifier.ABSTRACT | Modifier.INTERFACE)) == 0,
						"@Subscribed method \"" + method + "\" cannot subscribe to polymorphic classes");

				for (Class<?> psc = parameterClazz.getSuperclass(); psc != null; psc = psc.getSuperclass()) {
					if (weakSubscribers.containsKey(psc)) {
						throw new IllegalArgumentException("@Subscribed method \"" + method +
								"\" cannot subscribe to class which inherits from subscribed class \"" + psc + "\"");
					}
				}

				final String preferredName = "on";
				checkArgument(method.getName().equals(preferredName),
						"Subscribed method " + method + " should be named " + preferredName);

				builder.put(parameterClazz, new WeakSubscriber(object, method, sub.priority()));
				log.trace("Registered weak subscriber {} for {}", method, parameterClazz);
			}

			weakSubscribers = builder.build();
		}
	}

	/**
	 * Optional to unregister
	 */
	@Synchronized
	public void unregister(@Nonnull final Object object) {
		weakSubscribers = ImmutableMultimap.copyOf(Iterables.filter(
				weakSubscribers.entries(),
				entry -> !Objects.equals(entry.getValue().getObjectWeak().get(), object)
		));
	}

	public void post(@NonNull Object event) {
		log.trace("Posting event {}", event);
		weakSubscribers.get(event.getClass()).forEach(subscriber -> {
			try {
				subscriber.invoke(event);
			} catch (InvocationTargetException e) {
				Throwable targetException = e.getTargetException();
				if (targetException instanceof ProvisionException) {
					ProvisionException provisionException = (ProvisionException) targetException;
					for (Message errorMessage : provisionException.getErrorMessages()) {
						log.error("Error in subscriber {}:{}", subscriber.objectClass.getSimpleName(), errorMessage,
								provisionException.getCause());
					}
				}
				else {
					exceptionHandler.accept(targetException);
				}
			} catch (Exception e) {
				exceptionHandler.accept(e);
			}
		});
	}


}
