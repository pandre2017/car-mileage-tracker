# Per-car fueling estimates

The app estimates consumption and the odometer reading for the next usual fueling. It does not measure fuel remaining, predict an empty tank, or require a full-tank flag. Partial fills and changing fueling habits affect the estimates.

## Initial car configuration

In Firebase Console → Firestore Database → `cars` → the car document, add the optional number field `estimatedFullTankRangeKm`, for example `650`. Use a positive whole number no greater than 10,000,000; do not enter text or liters. The example is illustrative, not a recommendation for a particular car.

Deploy the updated backend to return this field, install the updated Android app, and tap **Refresh cars**. The value is cached for offline use. Existing cars without it remain compatible, and invalid provisioned values are treated as unconfigured by the API. Backup/export and restore preserve the optional field; older backups remain valid.

The app upgrades Room from version 1 to 2 by adding a nullable column. It preserves existing cars, pending/failed fuelings, and refresh timestamps; clearing app data is not required.

## Consumption

For the selected car, remove rejected records and sort remaining records by fueling date. Pending records are included. The compact statistics label uses 📉 for consumption and ⛽ for the next-fueling odometer, with a dash for unavailable values. Screen-reader descriptions retain the calculation basis, pending status, and limitations.

```text
estimated L/100 km = sum(liters added after the first record)
                    / (last odometer − first odometer) × 100
```

The first purchase is excluded because it is outside the measured distance. This is a distance-weighted estimate, not the arithmetic mean of per-interval rates. At least two records with increasing dates and odometer readings are needed. Values are rounded to two decimal places using decimal arithmetic. For example, 90 liters over 1,200 km gives 7.50 L/100 km.

The result assumes roughly similar fuel levels immediately after the first and last fueling. It can be distorted by partial fills.

## Next usual fueling

| Available records | Result |
| --- | --- |
| None | Show configured initial range only; no absolute odometer reading is invented. |
| One or two | Latest odometer plus initial range, if configured. The accessibility description states the assumption that the last fueling left a full tank. |
| Three or more | Latest odometer plus the average distance of the most recent five fueling intervals, or all available intervals if fewer than five. |

The learned estimate works without an initial range and is rounded to whole kilometers. It predicts a usual fueling stop rather than a near-empty tank. It is an odometer reading, not kilometers remaining or a calendar date. No additional current-odometer input is collected.

Duplicate dates or non-increasing odometer readings suppress both computed estimates and include a correction message in the accessibility description. Failed records and records belonging to another car do not contribute.

## Architecture and verification

`CarStatistics` owns pure Kotlin formulas in `:domain`. `ObserveCarStatisticsUseCase` observes cached cars and records and recalculates when either changes. It is constructor-injected into `MileageViewModel`, which exposes statistics as immutable UI state. `CarStatisticsLabel` only renders those values and has colocated previews. Nothing is persisted as a derived statistic, and no additional API calls or collection are needed.

Tests cover initial/missing configuration, weighted consumption, rounding, the five-interval window, pending/failed records, inconsistent history, per-car isolation, UseCase/ViewModel updates, data mapping, and the Room migration. Compose tests exercise populated and insufficient-data states using fixtures, without live Firebase access.
