CREATE TABLE `drivers` (
	`id` text PRIMARY KEY NOT NULL,
	`name` text NOT NULL,
	`email` text NOT NULL,
	`phone` text NOT NULL
);
--> statement-breakpoint
CREATE UNIQUE INDEX `drivers_email_unique` ON `drivers` (`email`);--> statement-breakpoint
CREATE TABLE `orders` (
	`id` text PRIMARY KEY NOT NULL,
	`customer` text NOT NULL,
	`phone` text NOT NULL,
	`address` text NOT NULL,
	`district` text NOT NULL,
	`amount` integer NOT NULL,
	`fee` integer NOT NULL,
	`payment` text NOT NULL,
	`driver_id` text,
	`status` text NOT NULL,
	`notes` text NOT NULL,
	`created` text NOT NULL,
	`delivered` text,
	`settled` integer DEFAULT 0 NOT NULL
);
--> statement-breakpoint
CREATE TABLE `workspace` (
	`id` integer PRIMARY KEY NOT NULL,
	`owner` text NOT NULL,
	`name` text NOT NULL
);
