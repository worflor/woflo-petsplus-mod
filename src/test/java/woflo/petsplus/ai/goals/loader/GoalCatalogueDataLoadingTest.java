package woflo.petsplus.ai.goals.loader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.entity.mob.MobEntity;
import org.junit.jupiter.api.Test;
import woflo.petsplus.ai.goals.AdaptiveGoal;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalCatalogueDataLoadingTest {

    @Test
    void allGoalDefinitionsHaveValidFactories() throws IOException {
        Path root = Path.of("src/main/resources/data/petsplus/goal_catalogue");
        List<Path> goalFiles = collectGoalFiles(root);
        assertFalse(goalFiles.isEmpty(), "Expected goal catalogue directory to contain definitions");

        for (Path file : goalFiles) {
            JsonObject json = parse(file);
            String factoryClass = json.get("goal_factory").getAsString();
            assertNotNull(factoryClass, "goal_factory must be defined for " + file);

            assertDoesNotThrow(
                () -> verifyFactory(factoryClass),
                () -> "Goal factory validation failed for " + file
            );
        }
    }

    private static void verifyFactory(String className) throws Exception {
        Class<?> raw = Class.forName(className, false, GoalCatalogueDataLoadingTest.class.getClassLoader());
        assertTrue(AdaptiveGoal.class.isAssignableFrom(raw),
            () -> "Factory class does not extend AdaptiveGoal: " + className);
        Constructor<?> ctor = raw.getDeclaredConstructor(MobEntity.class);
        assertNotNull(ctor, "Factory class must expose MobEntity constructor: " + className);
    }

    private static List<Path> collectGoalFiles(Path root) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().endsWith(".json"))
                .forEach(files::add);
        }
        return files;
    }

    private static JsonObject parse(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}

