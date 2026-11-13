"use strict";

// Generated mirror of UniversalExamRegistry.kt for server-side Community validation.
// The sync test fails if the canonical Kotlin registry changes without updating this file.
const COMMUNITY_SUBJECT_REGISTRY_SOURCE_SHA = "f38fac1acf2eb62bc1781fca220d6e2e5ad56f9b";
const COMMUNITY_SUBJECT_REGISTRY = [
  {
    "examId": "neet_ug",
    "examName": "NEET UG",
    "shortName": "NEET",
    "subjects": [
      "Physics",
      "Chemistry",
      "Botany",
      "Zoology"
    ]
  },
  {
    "examId": "jee_main",
    "examName": "JEE Main",
    "shortName": "JEE",
    "subjects": [
      "Physics",
      "Chemistry",
      "Mathematics"
    ]
  },
  {
    "examId": "cuet_ug",
    "examName": "CUET UG",
    "shortName": "CUET",
    "subjects": [
      "Domain Subject"
    ]
  },
  {
    "examId": "upsc_cse",
    "examName": "UPSC Civil Services",
    "shortName": "UPSC",
    "subjects": [
      "Indian Polity & Governance",
      "Economic & Social Development",
      "History of India & Art & Culture",
      "Geography & Environment Ecology",
      "General Science & Technology"
    ]
  },
  {
    "examId": "ssc_cgl",
    "examName": "SSC CGL",
    "shortName": "CGL",
    "subjects": [
      "General Intelligence & Reasoning",
      "General Awareness",
      "Quantitative Aptitude",
      "English Comprehension"
    ]
  },
  {
    "examId": "ssc_chsl",
    "examName": "SSC CHSL (10+2)",
    "shortName": "CHSL",
    "subjects": [
      "Reasoning",
      "General Awareness",
      "Quant Aptitude",
      "English Language"
    ]
  },
  {
    "examId": "ssc_mts",
    "examName": "SSC MTS & Havaldar",
    "shortName": "MTS",
    "subjects": [
      "Numerical Ability (No Neg)",
      "Reasoning (No Neg)",
      "General Awareness",
      "English Language"
    ]
  },
  {
    "examId": "ssc_gd",
    "examName": "SSC GD Constable",
    "shortName": "GD",
    "subjects": [
      "General Intelligence",
      "General Knowledge",
      "Elementary Mathematics",
      "English/Hindi"
    ]
  },
  {
    "examId": "ssc_cpo",
    "examName": "SSC CPO (Sub-Inspector)",
    "shortName": "CPO",
    "subjects": [
      "Reasoning",
      "General Knowledge",
      "Quant",
      "English"
    ]
  },
  {
    "examId": "rrb_ntpc",
    "examName": "RRB NTPC",
    "shortName": "NTPC",
    "subjects": [
      "General Awareness",
      "Mathematics",
      "Reasoning"
    ]
  },
  {
    "examId": "rrb_group_d",
    "examName": "RRB Group D",
    "shortName": "Group D",
    "subjects": [
      "General Science",
      "Mathematics",
      "Reasoning",
      "Current Affairs"
    ]
  },
  {
    "examId": "rrb_alp",
    "examName": "RRB ALP",
    "shortName": "ALP",
    "subjects": [
      "Mathematics",
      "Reasoning",
      "General Science",
      "General Awareness"
    ]
  },
  {
    "examId": "rrb_technician",
    "examName": "RRB Technician",
    "shortName": "Technician",
    "subjects": [
      "Mathematics",
      "General Intelligence",
      "General Science",
      "General Awareness"
    ]
  },
  {
    "examId": "rrb_je",
    "examName": "RRB JE",
    "shortName": "RRB JE",
    "subjects": [
      "Mathematics",
      "General Intelligence",
      "General Awareness",
      "General Science"
    ]
  },
  {
    "examId": "ctet",
    "examName": "CTET",
    "shortName": "CTET",
    "subjects": [
      "Child Development & Pedagogy",
      "Language I (Compulsory)",
      "Language II (Compulsory)",
      "Mathematics & Pedagogy",
      "Environmental Studies (EVS)"
    ]
  },
  {
    "examId": "ugc_net",
    "examName": "UGC NET",
    "shortName": "UGC NET",
    "subjects": [
      "Teaching & Research Aptitude"
    ]
  },
  {
    "examId": "cat",
    "examName": "CAT",
    "shortName": "CAT",
    "subjects": [
      "Verbal Ability & Reading Comprehension",
      "Data Interpretation & Logical Reasoning",
      "Quantitative Ability"
    ]
  },
  {
    "examId": "ibps_po",
    "examName": "IBPS PO",
    "shortName": "IBPS PO",
    "subjects": [
      "English Language",
      "Quantitative Aptitude",
      "Reasoning Ability"
    ]
  },
  {
    "examId": "ibps_clerk",
    "examName": "IBPS Clerk",
    "shortName": "IBPS Clerk",
    "subjects": [
      "English Language",
      "Numerical Ability",
      "Reasoning Ability"
    ]
  },
  {
    "examId": "sbi_clerk",
    "examName": "SBI Clerk",
    "shortName": "SBI Clerk",
    "subjects": [
      "English Language",
      "Numerical Ability",
      "Reasoning Ability"
    ]
  },
  {
    "examId": "sbi_po",
    "examName": "SBI PO",
    "shortName": "SBI PO",
    "subjects": [
      "English Language",
      "Quantitative Aptitude",
      "Reasoning Ability"
    ]
  },
  {
    "examId": "nda",
    "examName": "NDA & NA",
    "shortName": "NDA",
    "subjects": [
      "Mathematics",
      "English",
      "General Knowledge",
      "Elementary Mathematics"
    ]
  },
  {
    "examId": "gate",
    "examName": "GATE",
    "shortName": "GATE",
    "subjects": [
      "General Aptitude",
      "Engineering Mathematics & Technical Core"
    ]
  }
];

function findCommunityExam(examName) {
  const query = typeof examName === "string" ? examName.trim().toLowerCase() : "";
  if (!query) return null;
  const exact = COMMUNITY_SUBJECT_REGISTRY.find(exam =>
    exam.examName.toLowerCase() === query ||
    exam.shortName.toLowerCase() === query ||
    exam.examId.toLowerCase() === query
  );
  if (exact) return exact;
  return COMMUNITY_SUBJECT_REGISTRY.find(exam =>
    exam.examName.toLowerCase().includes(query) ||
    query.includes(exam.examName.toLowerCase()) ||
    query.includes(exam.shortName.toLowerCase())
  ) || null;
}

function getCommunityRegisteredSubjects(examName) {
  return findCommunityExam(examName)?.subjects || [];
}

function isCommunitySubjectValid(examName, subject) {
  const cleanSubject = typeof subject === "string" ? subject.trim() : "";
  return cleanSubject.length > 0 && getCommunityRegisteredSubjects(examName).includes(cleanSubject);
}

module.exports = {
  COMMUNITY_SUBJECT_REGISTRY_SOURCE_SHA,
  COMMUNITY_SUBJECT_REGISTRY,
  findCommunityExam,
  getCommunityRegisteredSubjects,
  isCommunitySubjectValid,
};
