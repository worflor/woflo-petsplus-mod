package woflo.petsplus.data;

import com.google.gson.*;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import woflo.petsplus.api.registry.PetRoleType;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the data loading system.
 * Tests the actual PetRoleDefinition parser and BaseJsonDataLoader logic,
 * not just generic GSON functionality.
 */
@DisplayName("Data Loaders")
class DataLoaderTest {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    @Nested
    @DisplayName("Real Role Definition Parsing")
    class RealRoleDefinitionTests {

        /**
         * Loads actual role JSON from resources and parses it.
         * This ensures our parser handles the REAL datapack files.
         */
        private PetRoleDefinition loadRoleFromResources(String roleName) throws Exception {
            String resourcePath = "/data/petsplus/roles/" + roleName + ".json";
            try (var stream = getClass().getResourceAsStream(resourcePath)) {
                assertThat(stream)
                    .withFailMessage("Role file not found: " + resourcePath)
                    .isNotNull();
                
                String jsonContent = new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                JsonObject jsonObj = GSON.fromJson(jsonContent, JsonObject.class);
                Identifier roleId = Identifier.of("petsplus", roleName);
                
                return PetRoleDefinition.fromJson(roleId, jsonObj, resourcePath);
            }
        }

        @Test
        @DisplayName("should parse REAL guardian.json from datapack")
        void parseRole_guardian() throws Exception {
            // When: Load actual guardian.json
            PetRoleDefinition guardian = loadRoleFromResources("guardian");

            // Then: Should match the actual file structure
            assertThat(guardian.id()).isEqualTo(Identifier.of("petsplus", "guardian"));
            assertThat(guardian.translationKey()).isEqualTo("petsplus.role.guardian");
            assertThat(guardian.archetype()).isEqualTo(PetRoleType.RoleArchetype.TANK);
            
            // Verify actual stat scalars from guardian.json
            assertThat(guardian.baseStatScalars())
                .containsEntry("defense", 0.02f);
                
            // Verify stat affinities match real file
            assertThat(guardian.statAffinities())
                .containsEntry("health", 0.05f)
                .containsEntry("defense", 0.05f)
                .containsEntry("learning", 0.02f);
            
            // Guardian has NO default abilities - they unlock via level_rewards!
            assertThat(guardian.defaultAbilities()).isEmpty();
            
            // Verify real attribute scaling
            assertThat(guardian.attributeScaling().healthBonusPerLevel()).isCloseTo(0.02f, within(0.001f));
            assertThat(guardian.attributeScaling().healthSoftcapLevel()).isEqualTo(20);
            assertThat(guardian.attributeScaling().healthMaxBonus()).isCloseTo(2.0f, within(0.001f));
            
            // Verify XP curve from real file
            assertThat(guardian.xpCurve().maxLevel()).isEqualTo(30);
            assertThat(guardian.xpCurve().baseLinearPerLevel()).isEqualTo(20);
            assertThat(guardian.xpCurve().quadraticFactor()).isEqualTo(8);
            
            // Verify real colors (not made up ones!)
            assertThat(guardian.visual().primaryColor()).isEqualTo(0x4AA3F0);
            assertThat(guardian.visual().secondaryColor()).isEqualTo(0x1F6DB5);
            
            // Verify passive aura from real file
            assertThat(guardian.passiveAuras()).hasSize(1);
            PetRoleType.PassiveAura shieldAura = guardian.passiveAuras().get(0);
            assertThat(shieldAura.id()).isEqualTo("guardian_shield");
            assertThat(shieldAura.intervalTicks()).isEqualTo(160);
            assertThat(shieldAura.radius()).isCloseTo(8.0, within(0.01));
            
            // Verify aura effects match real guardian.json
            assertThat(shieldAura.effects()).hasSize(2);
            PetRoleType.AuraEffect resistanceEffect = shieldAura.effects().get(0);
            assertThat(resistanceEffect.effectId()).isEqualTo(Identifier.of("minecraft", "resistance"));
            assertThat(resistanceEffect.target()).isEqualTo(PetRoleType.AuraTarget.OWNER);
            assertThat(resistanceEffect.durationTicks()).isEqualTo(200);
            assertThat(resistanceEffect.amplifier()).isEqualTo(0);
            
            PetRoleType.AuraEffect absorptionEffect = shieldAura.effects().get(1);
            assertThat(absorptionEffect.effectId()).isEqualTo(Identifier.of("minecraft", "absorption"));
            assertThat(absorptionEffect.durationTicks()).isEqualTo(120);
            
            // Verify level rewards exist (guardian has tribute milestones at 10, 20, 30)
            assertThat(guardian.levelRewards()).isNotEmpty();
            assertThat(guardian.levelRewards()).containsKeys(1, 3, 7, 10, 12, 17, 20, 23, 27, 30);
        }

        @Test
        @DisplayName("should parse REAL support.json from datapack")
        void parseRole_support() throws Exception {
            // When: Load actual support.json
            PetRoleDefinition support = loadRoleFromResources("support");

            // Then: Should match actual support role structure
            assertThat(support.id()).isEqualTo(Identifier.of("petsplus", "support"));
            assertThat(support.archetype()).isEqualTo(PetRoleType.RoleArchetype.SUPPORT);
            
            // Support has aura stat scalar
            assertThat(support.baseStatScalars()).containsEntry("aura", 0.02f);
            
            // Verify support-specific stat affinities
            assertThat(support.statAffinities())
                .containsEntry("vitality", 0.05f)
                .containsEntry("health", 0.03f)
                .containsEntry("learning", 0.04f);
            
            // Support has passive aura that requires sitting
            assertThat(support.passiveAuras()).hasSize(1);
            PetRoleType.PassiveAura restAura = support.passiveAuras().get(0);
            assertThat(restAura.id()).isEqualTo("support_companion_rest");
            assertThat(restAura.intervalTicks()).isEqualTo(60);
            assertThat(restAura.requireSitting()).isTrue();
            
            // Verify support has the special support_potion behavior
            assertThat(support.supportPotionBehavior()).isNotNull();
            assertThat(support.supportPotionBehavior().intervalTicks()).isEqualTo(140);
            assertThat(support.supportPotionBehavior().applyToPet()).isTrue();
            assertThat(support.supportPotionBehavior().fallbackEffect())
                .isEqualTo(Identifier.of("minecraft", "regeneration"));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "guardian",
            "striker", 
            "support",
            "scout",
            "skyrider",
            "cursed_one",
            "eclipsed",
            "enchantment_bound",
            "eepy_eeper"
        })
        @DisplayName("should successfully parse ALL real role files without errors")
        void parseRole_allRolesValid(String roleName) throws Exception {
            // When: Load each real role file
            PetRoleDefinition role = loadRoleFromResources(roleName);

            // Then: All roles should parse successfully
            assertThat(role).isNotNull();
            assertThat(role.id()).isEqualTo(Identifier.of("petsplus", roleName));
            assertThat(role.archetype()).isNotNull();
            assertThat(role.translationKey()).isNotBlank();
            
            // All roles should have visual metadata
            assertThat(role.visual()).isNotNull();
            assertThat(role.visual().primaryColor()).isNotZero();
            assertThat(role.visual().secondaryColor()).isNotZero();
            
            // All roles should have XP curve
            assertThat(role.xpCurve()).isNotNull();
            assertThat(role.xpCurve().maxLevel()).isGreaterThan(0);
            
            // All roles should have presentation metadata
            assertThat(role.presentation()).isNotNull();
        }

        @Test
        @DisplayName("should handle invalid/missing archetype gracefully")
        void parseRole_invalidArchetype() {
            // Given: Invalid archetype value
            String json = """
                {
                    "id": "petsplus:test",
                    "archetype": "INVALID_TYPE"
                }
                """;
            JsonObject jsonObj = GSON.fromJson(json, JsonObject.class);
            Identifier sourceId = Identifier.of("petsplus", "test");

            // When: Parse (should log warning but not crash)
            PetRoleDefinition definition = PetRoleDefinition.fromJson(sourceId, jsonObj, "test");

            // Then: Should use null archetype (builder will use default)
            assertThat(definition.archetype()).isNull();
        }

        @Test
        @DisplayName("should parse REAL striker.json with DPS archetype and attack scaling")
        void parseRole_striker() throws Exception {
            // When: Load actual striker.json
            PetRoleDefinition striker = loadRoleFromResources("striker");

            // Then: Should have DPS archetype with attack focus
            assertThat(striker.archetype()).isEqualTo(PetRoleType.RoleArchetype.DPS);
            assertThat(striker.baseStatScalars()).containsEntry("offense", 0.02f);
            
            // Striker has attack-focused attribute scaling
            assertThat(striker.attributeScaling().attackBonusPerLevel()).isCloseTo(0.02f, within(0.0001f));
            assertThat(striker.attributeScaling().attackSoftcapLevel()).isEqualTo(15);
            assertThat(striker.attributeScaling().attackMaxBonus()).isCloseTo(1.5f, within(0.0001f));
            
            // Striker stat affinities
            assertThat(striker.statAffinities())
                .containsEntry("attack", 0.05f)
                .containsEntry("speed", 0.03f);
        }

        @Test
        @DisplayName("should parse REAL scout.json with MOBILITY archetype")
        void parseRole_scout() throws Exception {
            // When: Load actual scout.json
            PetRoleDefinition scout = loadRoleFromResources("scout");

            // Then: Should have MOBILITY archetype
            assertThat(scout.archetype()).isEqualTo(PetRoleType.RoleArchetype.MOBILITY);
            
            // Scout should have learning as primary stat affinity
            assertThat(scout.statAffinities()).containsKey("learning");
        }

        @Test
        @DisplayName("should parse color values from real guardian.json")
        void parseRole_realColorValues() throws Exception {
            // When: Load guardian with real hex colors
            PetRoleDefinition guardian = loadRoleFromResources("guardian");

            // Then: Colors from guardian.json should be parsed correctly
            assertThat(guardian.visual().primaryColor()).isEqualTo(0x4AA3F0);
            assertThat(guardian.visual().secondaryColor()).isEqualTo(0x1F6DB5);
        }

        @Test
        @DisplayName("should parse real XP curves from all role files")
        void parseRole_realXpCurves() throws Exception {
            // When: Load guardian with real XP curve
            PetRoleDefinition guardian = loadRoleFromResources("guardian");

            // Then: XP curve from guardian.json should match
            PetRoleType.XpCurve curve = guardian.xpCurve();
            assertThat(curve.maxLevel()).isEqualTo(30);
            assertThat(curve.featureLevels()).containsExactly(3, 7, 12, 17, 23, 27);
            assertThat(curve.tributeMilestones()).containsExactly(10, 20, 30);
            assertThat(curve.baseLinearPerLevel()).isEqualTo(20);
            assertThat(curve.quadraticFactor()).isEqualTo(8);
            assertThat(curve.featureLevelBonusMultiplier()).isEqualTo(0.75f);
        }

        @Test
        @DisplayName("should parse real attribute scaling from striker.json")
        void parseRole_realAttributeScaling() throws Exception {
            // When: Load striker with attack-focused scaling
            PetRoleDefinition striker = loadRoleFromResources("striker");

            // Then: Real attribute scaling should be parsed
            PetRoleType.AttributeScaling scaling = striker.attributeScaling();
            assertThat(scaling.attackBonusPerLevel()).isCloseTo(0.02f, within(0.0001f));
            assertThat(scaling.attackPostSoftcapBonusPerLevel()).isCloseTo(0.01f, within(0.0001f));
            assertThat(scaling.attackSoftcapLevel()).isEqualTo(15);
            assertThat(scaling.attackMaxBonus()).isCloseTo(1.5f, within(0.0001f));
        }

        @Test
        @DisplayName("should parse real passive auras from guardian and support")
        void parseRole_realPassiveAuras() throws Exception {
            // When: Load guardian with shield aura
            PetRoleDefinition guardian = loadRoleFromResources("guardian");

            // Then: Real aura from guardian.json should be parsed
            assertThat(guardian.passiveAuras()).hasSize(1);
            PetRoleType.PassiveAura shieldAura = guardian.passiveAuras().get(0);
            assertThat(shieldAura.id()).isEqualTo("guardian_shield");
            assertThat(shieldAura.intervalTicks()).isEqualTo(160);
            assertThat(shieldAura.radius()).isCloseTo(8.0, within(0.01));
            assertThat(shieldAura.effects()).hasSize(2);
            
            // Verify real effects from guardian.json
            assertThat(shieldAura.effects().get(0).effectId())
                .isEqualTo(Identifier.of("minecraft", "resistance"));
            assertThat(shieldAura.effects().get(1).effectId())
                .isEqualTo(Identifier.of("minecraft", "absorption"));
        }

        @Test
        @DisplayName("should handle roles with empty default_abilities (abilities unlock via level_rewards)")
        void parseRole_emptyDefaultAbilities() throws Exception {
            // Most roles have empty default_abilities - abilities unlock through leveling
            PetRoleDefinition guardian = loadRoleFromResources("guardian");
            PetRoleDefinition support = loadRoleFromResources("support");
            PetRoleDefinition striker = loadRoleFromResources("striker");

            // Then: Real roles use level_rewards, not default_abilities
            assertThat(guardian.defaultAbilities()).isEmpty();
            assertThat(support.defaultAbilities()).isEmpty();
            assertThat(striker.defaultAbilities()).isEmpty();
            
            // But they all have level rewards
            assertThat(guardian.levelRewards()).isNotEmpty();
            assertThat(support.levelRewards()).isNotEmpty();
            assertThat(striker.levelRewards()).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("BaseJsonDataLoader Logic")
    class BaseDataLoaderTests {

        @Test
        @DisplayName("should convert resource file ID to resource ID correctly")
        void toResourceId_stripsPathAndExtension() {
            // Given: A data loader instance
            TestDataLoader loader = new TestDataLoader();
            
            // When: Convert resource file paths
            Identifier result1 = loader.toResourceIdPublic(
                Identifier.of("petsplus", "roles/guardian.json")
            );
            Identifier result2 = loader.toResourceIdPublic(
                Identifier.of("minecraft", "roles/test_role.json")
            );

            // Then: Should strip roles/ prefix and .json extension
            assertThat(result1).isEqualTo(Identifier.of("petsplus", "guardian"));
            assertThat(result2).isEqualTo(Identifier.of("minecraft", "test_role"));
        }

        @Test
        @DisplayName("should describe source location correctly")
        void describeSource_formatsCorrectly() {
            // Given: A data loader
            TestDataLoader loader = new TestDataLoader();

            // When: Describe source
            String description = loader.describeSourcePublic(
                Identifier.of("petsplus", "guardian")
            );

            // Then: Should format as namespace:path/file.json
            assertThat(description).isEqualTo("petsplus:roles/guardian.json");
        }

        // Helper class to expose protected methods for testing
        private static class TestDataLoader extends BaseJsonDataLoader<String> {
            public TestDataLoader() {
                super("roles", "test_loader");
            }

            @Override
            protected String getResourceTypeName() {
                return "test";
            }

            @Override
            protected void apply(Map<Identifier, JsonElement> prepared, net.minecraft.resource.ResourceManager manager) {
                // No-op for testing
            }

            public Identifier toResourceIdPublic(Identifier resourceId) {
                return toResourceId(resourceId);
            }

            public String describeSourcePublic(Identifier fileId) {
                return describeSource(fileId);
            }
        }
    }
}

