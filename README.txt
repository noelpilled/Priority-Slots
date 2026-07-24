Priority Slots projection planner

Based on commit:
c8e4c623d47d64df7db6b8abbb8172aed04fad78

Apply from the repository root:

git apply --check /path/to/projection-planner.patch
git apply /path/to/projection-planner.patch

Then run:

./gradlew clean test
./gradlew run

The patch:
- adds BankTagProjectionPlanner
- adds BankTagProjectionPlannerTest
- reduces BankTagProjector to RuneLite mutation and lifecycle work
- preserves the current persisted schema and visible behavior

Manual regression checks:
1. Lantadyme wins over cadantine.
2. Cadantine replaces lantadyme after withdrawal.
3. Lantadyme replaces cadantine after deposit.
4. Moving the winner while enabled follows and persists.
5. Moving the frozen winner while disabled remains at the moved index after re-enable.
6. The disabled-move behavior also survives a profile round-trip.
7. An unrelated ordinary tagged item remains in the tab and may shift to another free cell.
