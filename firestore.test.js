const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const assert = require("node:assert");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = "demo-no-project";
const ALICE_UID = "alice_student_123";
const BOB_UID = "bob_student_456";
const ADMIN_UID = "admin_super_999";

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

// =========================================================================
// 1. UNAUTHENTICATED ACCESS SECURITY BOUNDS
// =========================================================================

test("Unauthenticated: cannot read or write Users_Data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("Users_Data").get());
  await assertFails(unauthDb.collection("Users_Data").doc("anyone").set({ name: "Hacker" }));
});

test("Unauthenticated: cannot read Config or admin_settings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("Config").doc("admin_settings").get());
  await assertFails(unauthDb.collection("admin_settings").doc("ad_units").get());
});

test("Unauthenticated: cannot write to Tests or Tests_Data", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("Tests").doc("t1").set({ title: "Fake Test" }));
  await assertFails(unauthDb.collection("Tests_Data").doc("t1").set({ title: "Fake Test Data" }));
});

// =========================================================================
// 2. PRIVILEGED FIELD PROTECTION (XP, RANK, VIP, ADMIN, BAN STATUS)
// =========================================================================

test("Student: CAN create own profile with standard fields", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("Users_Data").doc(ALICE_UID).set({
      uid: ALICE_UID,
      name: "Alice Sharma",
      email: "alice@test.com",
      targetExam: "NEET",
      averageScore: 0.0,
    })
  );
});

test("Student: CANNOT create profile with isBanned=true, isVip=true, or admin/role/xp/rank", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  
  // Attempt to self-assign VIP
  await assertFails(
    aliceDb.collection("Users_Data").doc(ALICE_UID).set({
      name: "Alice",
      isVip: true,
    })
  );

  // Attempt to self-assign admin role
  await assertFails(
    aliceDb.collection("Users_Data").doc(ALICE_UID).set({
      name: "Alice",
      role: "admin",
    })
  );

  // Attempt to self-assign XP
  await assertFails(
    aliceDb.collection("Users_Data").doc(ALICE_UID).set({
      name: "Alice",
      xp: 50000,
    })
  );

  // Attempt to self-assign Rank
  await assertFails(
    aliceDb.collection("Users_Data").doc(ALICE_UID).set({
      name: "Alice",
      rank: 1,
    })
  );
});

test("Student: CANNOT update protected fields (isBanned, isVip, role, admin, xp, rank)", async () => {
  // Seed Alice's initial profile
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("Users_Data").doc(ALICE_UID).set({
      uid: ALICE_UID,
      name: "Alice",
      isBanned: false,
      isVip: false,
      totalTestsAttempted: 2,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Valid update: name and targetExam
  await assertSucceeds(
    aliceDb.collection("Users_Data").doc(ALICE_UID).update({
      name: "Alice Updated",
      targetExam: "JEE Main",
    })
  );

  // Valid update: non-authoritative profile info and bookmarks
  await assertSucceeds(
    aliceDb.collection("Users_Data").doc(ALICE_UID).update({
      targetExam: "JEE Main",
      bookmarkedTests: ["mock_neet_1"],
    })
  );

  // Authoritative field update: student cannot directly alter maxScore or totalTestsAttempted -> FAILS
  await assertFails(
    aliceDb.collection("Users_Data").doc(ALICE_UID).update({
      totalTestsAttempted: 3,
      maxScore: 680,
    })
  );

  // Authoritative field update: student cannot alter cumulative ranking scores -> FAILS
  await assertFails(
    aliceDb.collection("Users_Data").doc(ALICE_UID).update({
      totalCumulativeScore: 1500,
      cumulativeScore_NEET: 720,
    })
  );

  // Malicious update: self-assign VIP -> FAILS
  await assertFails(
    aliceDb.collection("Users_Data").doc(ALICE_UID).update({
      isVip: true,
    })
  );

  // Malicious update: self-modify ban status -> FAILS
  await assertFails(
    aliceDb.collection("Users_Data").doc(ALICE_UID).update({
      isBanned: true,
    })
  );

  // Malicious update: self-assign XP -> FAILS
  await assertFails(
    aliceDb.collection("Users_Data").doc(ALICE_UID).update({
      xp: 9999,
    })
  );

  // Malicious update: self-assign rank -> FAILS
  await assertFails(
    aliceDb.collection("Users_Data").doc(ALICE_UID).update({
      rank: 1,
    })
  );
});

// =========================================================================
// 3. TEST ATTEMPT SUBMISSION & IMMUTABILITY (NO TAMPERING WITH PAST SCORES)
// =========================================================================

test("Student: CANNOT directly create authoritative server-verified attempts", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  
  // Attempting to bypass Cloud Function by writing verifiedServerSide=true directly -> FAILS
  await assertFails(
    aliceDb.collection("Users_Data").doc(ALICE_UID).collection("Attempts").doc("test_1_1700000000").set({
      testId: "test_1",
      score: 720,
      verifiedServerSide: true,
      countsTowardRanking: true,
      authoritativeScore: 720,
    })
  );

  // Writing unverified local attempt record with no ranking claims -> SUCCEEDS
  await assertSucceeds(
    aliceDb.collection("Users_Data").doc(ALICE_UID).collection("Attempts").doc("test_1_1700000000").set({
      testId: "test_1",
      score: 540,
      verifiedServerSide: false,
      countsTowardRanking: false,
      dateTaken: 1700000000,
    })
  );
});

test("Student: CANNOT alter/update or delete an existing submitted attempt", async () => {
  const attemptId = "test_1_1700000000";
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("Users_Data").doc(ALICE_UID).collection("Attempts").doc(attemptId).set({
      testId: "test_1",
      score: 400,
      correctCount: 100,
      dateTaken: 1700000000,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Attempt to maliciously increase score -> FAILS
  await assertFails(
    aliceDb.collection("Users_Data").doc(ALICE_UID).collection("Attempts").doc(attemptId).update({
      score: 720,
      correctCount: 180,
    })
  );

  // Attempt to delete past low score -> FAILS
  await assertFails(
    aliceDb.collection("Users_Data").doc(ALICE_UID).collection("Attempts").doc(attemptId).delete()
  );
});

test("Student: CANNOT read or write another student's test attempts", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("Users_Data").doc(BOB_UID).collection("Attempts").doc("bob_att_1").set({
      score: 600,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("Users_Data").doc(BOB_UID).collection("Attempts").doc("bob_att_1").get()
  );
  await assertFails(
    aliceDb.collection("Users_Data").doc(BOB_UID).collection("Attempts").doc("bob_att_2").set({
      score: 100,
    })
  );
});

// =========================================================================
// 4. UNPUBLISHED TESTS AND QUESTION HIDING
// =========================================================================

test("Student: CAN read published official test, CANNOT read draft test of other users", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    // Published test
    await ctx.firestore().collection("Tests").doc("published_test").set({
      title: "NEET Mock 1",
      status: "APPROVED",
      isPublic: true,
      creatorId: "admin",
    });

    // Draft test by Bob
    await ctx.firestore().collection("Tests").doc("bob_draft_test").set({
      title: "Bob Secret Mock",
      status: "Under Review",
      isPublic: false,
      creatorId: BOB_UID,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  
  // Alice can read published test
  await assertSucceeds(aliceDb.collection("Tests").doc("published_test").get());

  // Alice CANNOT read Bob's draft test
  await assertFails(aliceDb.collection("Tests").doc("bob_draft_test").get());

  // Bob CAN read his own draft test
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(bobDb.collection("Tests").doc("bob_draft_test").get());
});

test("Student: CANNOT read questions of an unpublished test", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    // Draft test
    await ctx.firestore().collection("Tests").doc("draft_exam").set({
      title: "Draft Exam",
      status: "DRAFT",
      isPublic: false,
      creatorId: "creator_someone",
    });
    // Question under draft test
    await ctx.firestore().collection("Tests").doc("draft_exam").collection("questions").doc("q1").set({
      text: "Secret Question?",
      answer: "A",
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  // Alice CANNOT read questions of draft test
  await assertFails(
    aliceDb.collection("Tests").doc("draft_exam").collection("questions").doc("q1").get()
  );
});

// =========================================================================
// 5. COMMUNITY POSTS AND DISCUSSIONS SECURITY
// =========================================================================

test("Student: CAN create community post with own creatorId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("Community_Posts").doc("alice_post_1").set({
      creatorId: ALICE_UID,
      authorName: "Alice",
      title: "Best revision strategy for Physics?",
      content: "How to master Rotational Motion?",
      upvotes: 0,
      comments: 0,
    })
  );
});

test("Student: CANNOT spoof isPinned or isOfficial on community post creation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("Community_Posts").doc("alice_fake_pinned").set({
      creatorId: ALICE_UID,
      title: "Important Notice",
      content: "Exam Cancelled",
      isPinned: true, // Malicious spoof
    })
  );

  await assertFails(
    aliceDb.collection("Community_Posts").doc("alice_fake_official").set({
      creatorId: ALICE_UID,
      title: "Official Announcement",
      content: "Free VIP for everyone",
      isOfficial: true, // Malicious spoof
    })
  );
});

test("Student: CAN upvote/comment on another's post, but CANNOT edit title/content or delete it", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("Community_Posts").doc("bob_post").set({
      creatorId: BOB_UID,
      title: "Bob's Question",
      content: "Need help with Organic Chemistry",
      upvotes: 5,
      views: 20,
      comments: 2,
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Alice upvotes Bob's post -> SUCCEEDS
  await assertSucceeds(
    aliceDb.collection("Community_Posts").doc("bob_post").update({
      upvotes: 6,
    })
  );

  // Alice tries to alter Bob's title -> FAILS
  await assertFails(
    aliceDb.collection("Community_Posts").doc("bob_post").update({
      title: "Bob's Hacked Title",
    })
  );

  // Alice tries to delete Bob's post -> FAILS
  await assertFails(
    aliceDb.collection("Community_Posts").doc("bob_post").delete()
  );
});

// =========================================================================
// 6. ADMIN CUSTOM CLAIMS VERIFICATION
// =========================================================================

test("Admin: user with { admin: true } custom claim CAN perform administrative actions", async () => {
  // Context with token.admin = true
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { admin: true }).firestore();

  // Admin can write to Config
  await assertSucceeds(
    adminDb.collection("Config").doc("admin_settings").set({
      dailyFreeLimit: 5,
      maintenanceMode: false,
    })
  );

  // Admin can ban a user
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("Users_Data").doc(BOB_UID).set({
      uid: BOB_UID,
      isBanned: false,
    });
  });

  await assertSucceeds(
    adminDb.collection("Users_Data").doc(BOB_UID).update({
      isBanned: true,
    })
  );

  // Admin can assign VIP
  await assertSucceeds(
    adminDb.collection("Users_Data").doc(BOB_UID).update({
      isVip: true,
    })
  );

  // Admin can publish official tests
  await assertSucceeds(
    adminDb.collection("Tests_Data").doc("official_neet_2026").set({
      title: "Official All India NEET Mock",
      status: "Published",
      isOfficial: true,
    })
  );
});

test("Non-admin: authenticated user WITHOUT { admin: true } CANNOT write to Config or ban users", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertFails(
    aliceDb.collection("Config").doc("admin_settings").set({
      maintenanceMode: true,
    })
  );

  await assertFails(
    aliceDb.collection("Users_Data").doc(BOB_UID).update({
      isBanned: true,
    })
  );
});

// =========================================================================
// 7. USER PRIVACY & PUBLIC LEADERBOARD SEPARATION
// =========================================================================

test("Privacy: Student CAN read own profile, CANNOT read another student's Users_Data", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("Users_Data").doc(BOB_UID).set({
      uid: BOB_UID,
      email: "bob_private@example.com",
      phoneNumber: "+919876543210",
      targetExam: "NEET",
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Bob can read his own profile
  await assertSucceeds(bobDb.collection("Users_Data").doc(BOB_UID).get());

  // Alice CANNOT read Bob's private profile document (Zero-Privacy Leak)
  await assertFails(aliceDb.collection("Users_Data").doc(BOB_UID).get());
});

test("Leaderboard: Students CAN read public leaderboard, CANNOT write directly without Admin/Server", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("Leaderboard").doc("rank_1").set({
      userId: BOB_UID,
      displayName: "Bob",
      score: 710,
      targetExam: "NEET",
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { admin: true }).firestore();

  // Alice can read public leaderboard rankings
  await assertSucceeds(aliceDb.collection("Leaderboard").doc("rank_1").get());
  await assertSucceeds(aliceDb.collection("Leaderboard").get());

  // Alice CANNOT create or modify leaderboard rankings (anti-tamper)
  await assertFails(
    aliceDb.collection("Leaderboard").doc("rank_hacked").set({
      userId: ALICE_UID,
      score: 720,
    })
  );

  // Admin / Server CAN write and update rankings
  await assertSucceeds(
    adminDb.collection("Leaderboard").doc("rank_1").update({
      score: 715,
    })
  );
});

test("Super Admin: abhi952817@gmail.com with verified email has full admin privileges", async () => {
  const ownerDb = testEnv.authenticatedContext("owner_uid_777", {
    email: "abhi952817@gmail.com",
    email_verified: true,
  }).firestore();

  // Can publish official tests
  await assertSucceeds(
    ownerDb.collection("Tests_Data").doc("official_test_01").set({
      title: "All India NEET Mock Test 1",
      targetExam: "NEET",
      status: "Published",
      isPublic: true,
      totalQuestions: 180,
    })
  );

  // Can write admin configurations
  await assertSucceeds(
    ownerDb.collection("admin_settings").doc("system_status").set({
      maintenanceMode: false,
      bannerText: "Welcome to AK Test Series",
    })
  );
});

test("User_Analytics: Student can log own analytics, but cannot forge events for other users", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Alice can log her own analytics event
  await assertSucceeds(
    aliceDb.collection("User_Analytics").doc("alice_event_1").set({
      userId: ALICE_UID,
      eventType: "SCREEN_TIME",
      screenName: "Dashboard",
      durationSeconds: 120,
      timestamp: Date.now()
    })
  );

  // Alice CANNOT log an event claiming to be Bob (forgery prevented)
  await assertFails(
    aliceDb.collection("User_Analytics").doc("forged_event_1").set({
      userId: BOB_UID,
      eventType: "FEATURE_USAGE",
      featureName: "AdminPanel",
      timestamp: Date.now()
    })
  );
});

test("Private Test Ownership: Creator can read private test, another student is denied", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { admin: true }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Admin creates Alice's private test
  await adminDb.collection("Tests_Data").doc("alice_private_test").set({
    title: "Alice Private Test",
    creatorId: ALICE_UID,
    isPublic: false,
    isOfficial: false,
    status: "Published"
  });

  // Alice can read her own private test
  await assertSucceeds(aliceDb.collection("Tests_Data").doc("alice_private_test").get());

  // Bob CANNOT read Alice's private test
  await assertFails(bobDb.collection("Tests_Data").doc("alice_private_test").get());
});

test("Private Test Ownership: ownerId and uploaderId are supported, missing owner fails closed", async () => {
  const adminDb = testEnv.authenticatedContext(ADMIN_UID, { admin: true }).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Test with ownerId
  await adminDb.collection("Tests_Data").doc("alice_owner_test").set({
    title: "Alice Owner Test",
    ownerId: ALICE_UID,
    isPublic: false,
    isOfficial: false,
    status: "Published"
  });
  await assertSucceeds(aliceDb.collection("Tests_Data").doc("alice_owner_test").get());
  await assertFails(bobDb.collection("Tests_Data").doc("alice_owner_test").get());

  // Legacy test with missing owner/creator fails closed (not public)
  await adminDb.collection("Tests_Data").doc("orphan_private_test").set({
    title: "Orphan Private Test",
    isPublic: false,
    isOfficial: false,
    status: "Published"
  });
  await assertFails(aliceDb.collection("Tests_Data").doc("orphan_private_test").get());
});

test("Shared_Community_Tests: Creator can update, but cannot alter creatorId or uploaderId", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Alice creates community test
  await assertSucceeds(
    aliceDb.collection("Shared_Community_Tests").doc("alice_comm_test").set({
      title: "Shared Community Test",
      creatorId: ALICE_UID,
      targetExam: "NEET"
    })
  );

  // Bob cannot delete or alter Alice's community test
  await assertFails(
    bobDb.collection("Shared_Community_Tests").doc("alice_comm_test").delete()
  );

  // Alice cannot reassign creatorId to Bob
  await assertFails(
    aliceDb.collection("Shared_Community_Tests").doc("alice_comm_test").update({
      creatorId: BOB_UID
    })
  );
});

