# Pets+ Adaptive Behavior System

This directory contains JSON behavior definitions that configure the adaptive AI system for pets.

## Structure

Each behavior file defines a complete behavior with the following structure:

- `goal_class`: The fully qualified class name of the goal implementation
- `mutex_flags`: Array of flags that determine mutual exclusivity with other goals
- `parameters`: Map of parameters specific to this behavior
- `requirements`: Conditions that must be met for this behavior to be considered
- `triggers`: Events that can activate this behavior
- `feedback`: Rewards and effects to apply when the behavior completes
- `associated_data`: Additional data used by the behavior
- `urgency_calculation`: Parameters for calculating the urgency score

## Example

```json
{
  "goal_class": "woflo.petsplus.ai.goals.social.ParallelPlayGoal",
  "mutex_flags": ["MOVEMENT", "INTERACTION"],
  "parameters": {
    "max_duration": 400,
    "comfortable_distance": 5.0
  },
  "requirements": {
    "allowed_moods": ["HAPPY", "PLAYFUL"],
    "min_energy_level": "NORMAL",
    "capability_requirements": ["has_owner"],
    "min_bond_level": 0.3
  },
  "urgency_calculation": {
    "base_urgency": 0.5,
    "mood_multipliers": {
      "PLAYFUL": 1.8,
      "HAPPY": 1.3
    }
  }
}
```

## Integration

The behavior system is integrated with the existing goal system through:

1. `IAdaptableGoal` interface - extends the existing `AdaptiveGoal` class
2. `GoalArbiter` - replaces the vanilla priority-based selector with urgency-based selection
3. `BehaviorManager` - loads and manages behavior definitions from JSON files
4. `AdaptiveAIManager` - coordinates the system for each pet

## Implementation Notes

- Goals that implement `IAdaptableGoal` can be configured via behavior data
- The system supports both JSON-driven and hardcoded goals
- Urgency scores are calculated dynamically based on pet state and context
- Mutex flags ensure incompatible behaviors don't run simultaneously