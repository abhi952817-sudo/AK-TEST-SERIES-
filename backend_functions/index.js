const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

// Use the same (default) Firestore database as the Android app.
const db = admin.firestore();
