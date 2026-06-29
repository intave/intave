package de.jpx3.intave.test;

import com.comphenix.protocol.utility.ByteBuddyGenerated;
import de.jpx3.intave.IntavePlugin;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.ByteCodeElement;
import net.bytebuddy.description.modifier.ModifierContributor;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

public final class FakeWorldFactory {
	public static World createWorld(BiFunction<String, Object[], Object> methodReturn) {
		List<BiFunction<String, Object[], Object>> methods = new ArrayList<>();
//    methods.add(FakePlayerFactory::identity);
		methods.add(methodReturn);

		try {
			return (World) worldConstructorForMethods(methods).newInstance(Bukkit.getServer());
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
			throw new RuntimeException(e);
		}
	}

	private static int COUNTER = 0;

	private static Constructor<?> worldConstructorForMethods(List<? extends BiFunction<String, Object[], Object>> methods) {
		MethodDelegation implementation = MethodDelegation.to(new Object() {
			@RuntimeType
			public Object delegate(@This Object obj, @Origin Method method, @FieldValue("server") Server server, @AllArguments Object... args) {
				String methodName = method.getName();
				for (BiFunction<String, Object[], Object> stringObjectBiFunction : methods) {
					Object result = stringObjectBiFunction.apply(methodName, args);
					if (result != null) {
						return result;
					}
				}
				throw new UnsupportedOperationException("Method " + methodName + " is not supported for this mock world");
			}
		});
		ElementMatcher.Junction<ByteCodeElement> callbackFilter = ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class)
			.or(ElementMatchers.isDeclaredBy(FakeWorld.class)));
		try {
			return createSubclass(FakeWorld.class, ConstructorStrategy.Default.NO_CONSTRUCTORS)
				.name(FakePlayerFactory.class.getPackage().getName() + ".WorldGenerator" + UUID.randomUUID().toString().substring(0, 8) + COUNTER++)
				.implement(new Type[]{World.class})
				.defineField("server", Server.class, new ModifierContributor.ForField[]{Visibility.PRIVATE})
				.defineConstructor(new ModifierContributor.ForMethod[]{Visibility.PUBLIC})
				.withParameters(new Type[]{Server.class})
				.intercept(MethodCall.invoke(FakeWorld.class.getDeclaredConstructor()).andThen(FieldAccessor.ofField("server").setsArgumentAt(0)))
				.method(callbackFilter)
				.intercept(implementation)
				.make()
				.load(IntavePlugin.class.getClassLoader(), ClassLoadingStrategy.Default.INJECTION)
				.getLoaded()
				.getDeclaredConstructor(Server.class);
		} catch (NoSuchMethodException var3) {
			throw new RuntimeException("Failed to find constructor", var3);
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	private static <T> DynamicType.Builder.MethodDefinition.ImplementationDefinition.Optional<T> createSubclass(Class<T> clz, ConstructorStrategy.Default constructorStrategy) {
		return (new ByteBuddy()).subclass(clz, constructorStrategy).implement(ByteBuddyGenerated.class);
	}
}
