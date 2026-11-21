const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const pastTs = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

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

test("Unauthenticated user: cannot read profiles, posts, stories, or conversations", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("profiles").where("isPublic", "==", true).get());
  await assertFails(unauthDb.collection("posts").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("stories").where("visibility", "==", "public").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Private PII isolation: Bob cannot read Alice's private /users/{uid} doc", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      email: "alice@example.com",
      darkModeEnabled: true,
      notificationsMuted: false,
      createdAt: pastTs(),
      updatedAt: pastTs(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());

  // Alice can update Instagram settings fields; Bob cannot
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      themeMode: "dark",
      hideLikeCounts: true,
      hiddenWords: ["spam"],
      blockedUserIds: [BOB_UID],
      updatedAt: pastTs(),
    })
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      hideLikeCounts: false,
      updatedAt: pastTs(),
    })
  );
});

test("Public profile creation blocks self-verification and spoofing, allows owner edit", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Fails if trying to set isVerified = true
  await assertFails(
    aliceDb.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_aura",
      displayName: "Alice",
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      isVerified: true,
      isPublic: true,
      createdAt: pastTs(),
      updatedAt: pastTs(),
    })
  );

  // Fails if ghost field added (Shadow Update Test)
  await assertFails(
    aliceDb.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_aura",
      displayName: "Alice",
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      isVerified: false,
      isPublic: true,
      isAdmin: true,
      createdAt: pastTs(),
      updatedAt: pastTs(),
    })
  );

  // Succeeds with valid payload
  await assertSucceeds(
    aliceDb.collection("profiles").doc(ALICE_UID).set({
      userId: ALICE_UID,
      username: "alice_aura",
      displayName: "Alice",
      bio: "Visual creator",
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      isVerified: false,
      isPublic: true,
      createdAt: pastTs(),
      updatedAt: pastTs(),
    })
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  // Bob can query public profiles
  await assertSucceeds(bobDb.collection("profiles").where("isPublic", "==", true).get());
  // Bob fails unfiltered query if private profile exists (rules are not filters)
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("profiles").doc("private_user").set({
      userId: "private_user",
      username: "hidden_user",
      displayName: "Hidden",
      followersCount: 0,
      followingCount: 0,
      postsCount: 0,
      isVerified: false,
      isPublic: false,
      createdAt: new Date(),
      updatedAt: new Date(),
    });
  });
  await assertFails(bobDb.collection("profiles").get());
});

test("Posts: owner create, public query, atomic like batch, cross-user delete rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("posts").doc("post_1").set({
      userId: ALICE_UID,
      authorUsername: "alice_aura",
      mediaType: "photo",
      imageData: "drawable:img_post_mountain_golden",
      caption: "Sunrise in the Alps",
      likesCount: 0,
      commentsCount: 0,
      visibility: "public",
      createdAt: pastTs(),
      updatedAt: pastTs(),
    })
  );

  // Bob can list public posts
  await assertSucceeds(bobDb.collection("posts").where("visibility", "==", "public").get());

  // Bob cannot delete Alice's post
  await assertFails(bobDb.collection("posts").doc("post_1").delete());

  // Bob cannot arbitrarily inflate likesCount without atomic liked_posts doc
  await assertFails(bobDb.collection("posts").doc("post_1").update({ likesCount: 999 }));

  // Bob CAN like post_1 atomically in a batch with /users/bob/liked_posts/post_1
  const batch = bobDb.batch();
  batch.set(bobDb.collection("users").doc(BOB_UID).collection("liked_posts").doc("post_1"), {
    userId: BOB_UID,
    postId: "post_1",
    createdAt: pastTs(),
  });
  batch.update(bobDb.collection("posts").doc("post_1"), { likesCount: 1 });
  await assertSucceeds(batch.commit());
});

test("Conversations & Messages: strict participant isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();

  const convId = `${ALICE_UID}_${BOB_UID}`;
  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).set({
      participantIds: [ALICE_UID, BOB_UID],
      userAId: ALICE_UID,
      userAName: "Alice",
      userBId: BOB_UID,
      userBName: "Bob",
      lastMessage: "Hey Bob!",
      lastSenderId: ALICE_UID,
      createdAt: pastTs(),
      updatedAt: pastTs(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("conversations").doc(convId).collection("messages").doc("msg_1").set({
      senderId: ALICE_UID,
      conversationId: convId,
      participantIds: [ALICE_UID, BOB_UID],
      text: "Hey Bob!",
      createdAt: pastTs(),
    })
  );

  // Bob can read conversation & messages
  await assertSucceeds(
    bobDb.collection("conversations").where("participantIds", "array-contains", BOB_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("conversations").doc(convId).collection("messages").where("participantIds", "array-contains", BOB_UID).get()
  );

  // Charlie cannot read or write in Alice-Bob conversation
  await assertFails(charlieDb.collection("conversations").doc(convId).get());
  await assertFails(
    charlieDb.collection("conversations").doc(convId).collection("messages").doc("msg_2").set({
      senderId: CHARLIE_UID,
      conversationId: convId,
      participantIds: [ALICE_UID, BOB_UID],
      text: "Intruder",
      createdAt: pastTs(),
    })
  );
});
