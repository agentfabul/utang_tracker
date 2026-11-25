const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_123";
const BOB_UID = "bob_uid_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any user, customer, or transaction", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const past = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).set({
      storeName: "Aling Nena Store",
      reminderTemplate: "Hi [Name]",
      createdAt: past,
    })
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("customers").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .collection("transactions")
      .get()
  );
});

test("User profile: owner can create and update settings; cross-user and ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      storeName: "Aling Nena Sari-Sari",
      reminderTemplate: "Hi [Name], friendly reminder po na may balance kayo na ₱[amount] sa [Store Name]. Salamat po!",
      createdAt: created,
      updatedAt: created,
    })
  );

  // Partial update of storeName & reminderTemplate succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      storeName: "Nena Mart",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Immutable createdAt mutation fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 500),
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Ghost field fails
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob cannot read or write Alice's profile
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ storeName: "Hijacked" })
  );
});

test("Customers CRUD: Kotlin-mirrored payloads succeed; invalid fields & cross-user fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 5000);

  // Create customer without optional fields (null fields omitted via filterValues)
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .set({
        name: "Juan Dela Cruz",
        nameLowercase: "juan dela cruz",
        balanceCentavos: 0,
        lastActivityAt: created,
        createdAt: created,
        updatedAt: created,
      })
  );

  // Create customer with optional fields
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_2")
      .set({
        name: "Maria Clara",
        nameLowercase: "maria clara",
        phone: "09171234567",
        notes: "Corner house",
        creditLimitCentavos: 100000,
        balanceCentavos: 0,
        lastActivityAt: created,
        createdAt: created,
        updatedAt: created,
      })
  );

  // List customers succeeds for owner, fails for Bob
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("customers").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("customers").get()
  );

  // Profile update succeeds
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_1")
      .update({
        name: "Juan D. Cruz",
        nameLowercase: "juan d. cruz",
        phone: "09181112222",
        lastActivityAt: new Date(Date.now() - 1000),
        updatedAt: new Date(Date.now() - 1000),
      })
  );

  // Reject @DocumentId 'id' inside payload
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_bad_id")
      .set({
        id: "cust_bad_id",
        name: "Bad",
        nameLowercase: "bad",
        balanceCentavos: 0,
        lastActivityAt: created,
        createdAt: created,
      })
  );

  // Reject blank name
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_blank")
      .set({
        name: "",
        nameLowercase: "",
        balanceCentavos: 0,
        lastActivityAt: created,
        createdAt: created,
      })
  );

  // Reject creditLimitCentavos <= 0
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("cust_bad_limit")
      .set({
        name: "Bad Limit",
        nameLowercase: "bad limit",
        creditLimitCentavos: 0,
        balanceCentavos: 0,
        lastActivityAt: created,
        createdAt: created,
      })
  );
});

test("Transactions CRUD and atomic balance update succeed; invalid amounts, types, and orphan writes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const created = new Date(Date.now() - 10000);

  const custRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("customers")
    .doc("cust_1");

  await assertSucceeds(
    custRef.set({
      name: "Pedro Santos",
      nameLowercase: "pedro santos",
      balanceCentavos: 0,
      lastActivityAt: created,
      createdAt: created,
      updatedAt: created,
    })
  );

  // Orphan transaction without existing parent customer must fail
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("customers")
      .doc("non_existent_customer")
      .collection("transactions")
      .doc("tx_orphan")
      .set({
        type: "CREDIT",
        amountCentavos: 5000,
        date: created,
        createdAt: created,
      })
  );

  // Atomic batch: create CREDIT transaction + update customer balanceCentavos
  const txRef = custRef.collection("transactions").doc("tx_1");
  const batch = aliceDb.batch();
  batch.set(txRef, {
    type: "CREDIT",
    amountCentavos: 15050,
    description: "2 Coke, 1 Lucky Me",
    date: new Date(Date.now() - 5000),
    createdAt: new Date(Date.now() - 5000),
    updatedAt: new Date(Date.now() - 5000),
  });
  batch.update(custRef, {
    balanceCentavos: 15050,
    lastActivityAt: new Date(Date.now() - 5000),
    updatedAt: new Date(Date.now() - 5000),
  });
  await assertSucceeds(batch.commit());

  // Payment transaction + balance & lastPaymentAt update
  const txPayRef = custRef.collection("transactions").doc("tx_2");
  const payTime = new Date(Date.now() - 2000);
  const payBatch = aliceDb.batch();
  payBatch.set(txPayRef, {
    type: "PAYMENT",
    amountCentavos: 5000,
    description: "Partial payment",
    date: payTime,
    createdAt: payTime,
    updatedAt: payTime,
  });
  payBatch.update(custRef, {
    balanceCentavos: 10050,
    lastPaymentAt: payTime,
    lastActivityAt: payTime,
    updatedAt: payTime,
  });
  await assertSucceeds(payBatch.commit());

  // Edit transaction
  await assertSucceeds(
    txRef.update({
      type: "CREDIT",
      amountCentavos: 16050,
      description: "2 Coke, 2 Lucky Me",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject amountCentavos <= 0
  await assertFails(
    custRef.collection("transactions").doc("tx_zero").set({
      type: "CREDIT",
      amountCentavos: 0,
      date: created,
      createdAt: created,
    })
  );

  // Reject invalid type
  await assertFails(
    custRef.collection("transactions").doc("tx_bad_type").set({
      type: "DISCOUNT",
      amountCentavos: 1000,
      date: created,
      createdAt: created,
    })
  );

  // Reject future createdAt timestamp
  await assertFails(
    custRef.collection("transactions").doc("tx_future").set({
      type: "CREDIT",
      amountCentavos: 1000,
      date: created,
      createdAt: new Date(Date.now() + 60000),
    })
  );

  // Bob cannot list, read, or delete Alice's transactions
  await assertFails(bobDb.doc(custRef.path).collection("transactions").get());
  await assertFails(bobDb.doc(txRef.path).get());
  await assertFails(bobDb.doc(txRef.path).delete());

  // Owner can delete transaction & customer
  await assertSucceeds(txRef.delete());
  await assertSucceeds(txPayRef.delete());
  await assertSucceeds(custRef.delete());
});
