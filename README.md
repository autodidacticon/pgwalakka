# walakka

#### Motivation
The Postgres write-ahead-log (WAL) may be accessed through the logical replication API and the resulting data may be streamed to an external service. Existing solutions effect this via single threaded processes listening to the WAL. Unfortunately, these approaches lead to scenarios where the WAL consumer falls far behind the producer (application) process resulting in continually growing WAL retention on disk. In other words, a single threaded WAL consumer can never catch up with the numerous application processes transacting with the database.

#### Design
walakka aims to solve this by creating additional replication slots when a fall behind scenario is detected. The high-level flow can be explained as follows:
1. Create a replication slot with a corresponding replication actor
2. While the replication actor processes the WAL, continually monitor how far behind the WAL consumer is from the tip of the WAL. This can be measured in disk space via the following query:
  ```
  select pg_xlog_location_diff(pg_current_xlog_insert_location(), restart_lsn) from pg_replication_slots where slot_name = $slotName
  ```
3. When the difference between the tip of the WAL and the restart_lsn exceeds a given threshold, **create a new replication slot and corresponding replication actor and update the previous replication actor with the LSN of the newly created slot (ie the tip of WAL)**.
    * When the first replication actor reaches the LSN of the newly created slot; terminate the actor and remove the replication slot. This indicates the first replication process has 'caught up'.
4. Continuously monitor and apply step 3 to newly created replication slots.

#### Caveats
* Messages will not be delivered in order in fall-behind scenarios.
* In general, given average producer rate of N GB / hour, replication rate of M GB / hour, and threshold T GB there can always be assumed to be (N - M) / T (rounded down) replication slots given a time interval of an hour.

#### Development Setup
1. `docker pull debezium/postgres:9.6`
2. `docker run -d -t -p 5432:5432 --name postgres postgres`
3. `sbt flywayMigrate`
4. `sbt "runMain io.walakka.WalAkka"`
