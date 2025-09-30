package woflo.petsplus;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;
import woflo.petsplus.datagen.PetsplusAdvancementProvider;
import woflo.petsplus.datagen.PetsplusEntityTagProvider;
import woflo.petsplus.datagen.SimpleDataGenerator;

public class PetsplusDataGenerator implements DataGeneratorEntrypoint {
	@Override
	public void onInitializeDataGenerator(FabricDataGenerator fabricDataGenerator) {
		FabricDataGenerator.Pack pack = fabricDataGenerator.createPack();
		
		// Register our entity tag provider with Fabric's data generation system
		pack.addProvider(PetsplusEntityTagProvider::new);
		
		// Register advancement provider with Fabric's data generation system
		pack.addProvider(PetsplusAdvancementProvider::new);
		
		// Generate our custom PetsPlus data files (abilities, roles, etc.)
		SimpleDataGenerator.generateAll();
	}
}
