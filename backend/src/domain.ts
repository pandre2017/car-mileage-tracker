import { z } from "zod";

export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}
export const inputSchema = z
  .object({
    fuelDate: z
      .string()
      .regex(/^\d{4}-\d{2}-\d{2}$/)
      .refine((value) => {
        const date = new Date(`${value}T12:00:00Z`);
        return (
          !Number.isNaN(date.getTime()) &&
          date.toISOString().slice(0, 10) === value
        );
      }, "Invalid calendar date"),
    odometerKm: z.number().int().min(0).max(10_000_000),
    litersHundredths: z.number().int().positive().max(100_000),
    totalPriceCents: z.number().int().positive().max(10_000_000),
  })
  .strict();
export type FuelInput = z.infer<typeof inputSchema>;
export type Entry = FuelInput & {
  id: string;
  createdByUid: string;
  createdAt: string;
};
export type Profile = { uid: string; displayName: string; email: string };
export type Car = { id: string; displayName: string };
export function validateHistory(input: FuelInput, history: Entry[]): void {
  if (history.some((entry) => entry.fuelDate === input.fuelDate)) {
    throw new ApiError(
      409,
      "DATE_EXISTS",
      "This car already has a record for that date.",
    );
  }
  if (
    history.some(
      (entry) =>
        entry.fuelDate < input.fuelDate && entry.odometerKm >= input.odometerKm,
    ) ||
    history.some(
      (entry) =>
        entry.fuelDate > input.fuelDate && entry.odometerKm <= input.odometerKm,
    )
  ) {
    throw new ApiError(
      409,
      "ODOMETER_CONFLICT",
      "The odometer must fit between earlier and later records.",
    );
  }
}
export function sameInput(
  entry: Entry,
  input: FuelInput,
  uid: string,
): boolean {
  return (
    entry.createdByUid === uid &&
    entry.fuelDate === input.fuelDate &&
    entry.odometerKm === input.odometerKm &&
    entry.litersHundredths === input.litersHundredths &&
    entry.totalPriceCents === input.totalPriceCents
  );
}
export interface Store {
  profile(uid: string): Promise<Profile>;
  cars(uid: string): Promise<Car[]>;
  history(uid: string, carId: string): Promise<Entry[]>;
  create(
    uid: string,
    carId: string,
    id: string,
    input: FuelInput,
  ): Promise<{ entry: Entry; created: boolean }>;
}
