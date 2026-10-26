package Com.ak.testseries.exam

import Com.ak.testseries.data.SyllabusEntity

/**
 * Authentic Chapter-Wise Syllabus Catalog & Weightage Matrix for Indian Competitive Exams.
 * Covers NEET UG, JEE Mains, UPSC CDS, and SSC CGL with estimated exam weightage percentages,
 * question counts, core topics, and priority ratings.
 */
data class ChapterDetail(
    val examId: String,
    val subject: String,
    val chapter: String,
    val classLevel: String = "Class 12",
    val weightagePercent: Int, // e.g. 8 for 8%
    val priority: String = "High", // "High", "Medium", "Core"
    val keyTopics: List<String> = emptyList(),
    val totalQuestionsExpected: Int = 2
)

object SyllabusCatalog {

    fun getChapterDetailsForExam(targetExam: String): List<ChapterDetail> {
        val upper = targetExam.uppercase()
        return when {
            upper.contains("ADVANCED") || upper == "JEE_ADVANCED" -> emptyList() // Isolated: JEE Advanced verified chapter catalog is distinct from JEE Main
            upper.contains("NEET") || upper.contains("MEDICAL") -> neetUgSyllabus
            upper.contains("JEE") || upper.contains("IIT") || upper.contains("ENGINEERING") -> jeeMainSyllabus
            upper.contains("CDS") || upper.contains("UPSC") || upper.contains("NDA") || upper.contains("DEFEN") -> upscCdsSyllabus
            upper.contains("SSC") || upper.contains("CGL") || upper.contains("GOVT") -> sscCglSyllabus
            else -> emptyList()
        }
    }

    fun getSyllabusEntitiesForExam(targetExam: String): List<SyllabusEntity> {
        val details = getChapterDetailsForExam(targetExam)
        return details.mapIndexed { idx, ch ->
            SyllabusEntity(
                id = "${ch.examId.lowercase()}_${ch.subject.lowercase().replace(" ", "_")}_${idx + 1}",
                classLevel = ch.classLevel,
                subject = ch.subject,
                chapter = ch.chapter,
                topics = ch.keyTopics.joinToString(", "),
                totalTests = maxOf(4, ch.weightagePercent * 2)
            )
        }
    }

    fun getAllSyllabusEntities(): List<SyllabusEntity> {
        val all = neetUgSyllabus + jeeMainSyllabus + upscCdsSyllabus + sscCglSyllabus
        return all.mapIndexed { idx, ch ->
            SyllabusEntity(
                id = "${ch.examId.lowercase()}_${ch.subject.lowercase().replace(" ", "_")}_${idx + 1}",
                classLevel = ch.classLevel,
                subject = ch.subject,
                chapter = ch.chapter,
                topics = ch.keyTopics.joinToString(", "),
                totalTests = maxOf(4, ch.weightagePercent * 2)
            )
        }
    }

    // =========================================================================
    // 1. NEET UG SYLLABUS & WEIGHTAGE (Physics, Chemistry, Botany, Zoology)
    // =========================================================================
    val neetUgSyllabus = listOf(
        // --- PHYSICS ---
        ChapterDetail("NEET", "Physics", "Rotational Motion & System of Particles", "Class 11", 7, "High",
            listOf("Center of Mass & Velocity", "Torque & Angular Momentum Conservation", "Moment of Inertia Theorems", "Pure Rolling Motion on Incline"), 3),
        ChapterDetail("NEET", "Physics", "Thermodynamics & Heat Transfer", "Class 11", 8, "High",
            listOf("First Law of Thermodynamics", "Isothermal, Adiabatic & Isochoric Processes", "Carnot Engine & Efficiency", "Conduction, Convection & Radiation"), 3),
        ChapterDetail("NEET", "Physics", "Current Electricity", "Class 12", 8, "High",
            listOf("Ohm's Law, Drift Velocity & Mobility", "Kirchhoff's Laws & Loop Rule", "Wheatstone Bridge & Potentiometer Principle", "Series-Parallel Cells & Resistors"), 3),
        ChapterDetail("NEET", "Physics", "Optics (Ray & Wave Optics)", "Class 12", 10, "High",
            listOf("Total Internal Reflection & Critical Angle", "Lens Maker Formula & Prism Deviation", "Young's Double Slit Experiment (YDSE)", "Diffraction & Resolving Power"), 5),
        ChapterDetail("NEET", "Physics", "Modern Physics & Dual Nature of Radiation", "Class 12", 10, "High",
            listOf("Photoelectric Equation & Stopping Potential", "de-Broglie Matter Waves", "Bohr Atomic Model & Spectral Series", "Radioactivity & Nuclear Binding Energy"), 5),
        ChapterDetail("NEET", "Physics", "Electrostatics & Capacitance", "Class 12", 8, "High",
            listOf("Coulomb's Law in Vector Form", "Electric Dipole & Gauss's Law", "Equipotential Surfaces", "Capacitors with Dielectric Slabs"), 3),
        ChapterDetail("NEET", "Physics", "Moving Charges & Magnetism", "Class 12", 7, "High",
            listOf("Biot-Savart Law & Circular Coil Field", "Ampere's Circuital Law", "Magnetic Force on Wire & Charge", "Moving Coil Galvanometer Sensitivity"), 3),
        ChapterDetail("NEET", "Physics", "Kinematics: 1D & 2D Motion", "Class 11", 6, "Medium",
            listOf("Equations of Uniform Acceleration", "Projectile Motion & Maximum Range", "Relative Velocity & River-Boat Problems", "Centripetal Acceleration"), 2),
        ChapterDetail("NEET", "Physics", "Laws of Motion & Friction", "Class 11", 6, "Medium",
            listOf("Newton's Laws & Free Body Diagrams", "Static, Kinetic & Rolling Friction", "Banking of Curved Tracks", "Connected Pulley-Block Systems"), 2),
        ChapterDetail("NEET", "Physics", "Electromagnetic Induction & Alternating Current", "Class 12", 7, "Medium",
            listOf("Faraday's & Lenz's Induction Law", "Self & Mutual Inductance Coefficients", "LCR Series Circuit Resonance", "Power Factor & Step-up/down Transformers"), 3),
        ChapterDetail("NEET", "Physics", "Semiconductor Electronics & Logic Gates", "Class 12", 6, "High",
            listOf("Energy Bands (Conductor, Semiconductor, Insulator)", "p-n Junction Diode V-I Characteristics", "Zener Diode Voltage Regulator", "AND, OR, NOT, NAND, NOR Gates"), 3),
        ChapterDetail("NEET", "Physics", "Gravitation", "Class 11", 5, "Medium",
            listOf("Newton's Universal Gravitation", "Variation of g with Altitude & Depth", "Gravitational Potential Energy", "Orbital & Escape Velocity"), 2),
        ChapterDetail("NEET", "Physics", "Work, Energy & Power", "Class 11", 5, "Medium",
            listOf("Work-Energy Theorem", "Conservative vs Non-Conservative Forces", "Elastic & Inelastic Collisions", "Spring Potential Energy"), 2),
        ChapterDetail("NEET", "Physics", "Oscillations & Simple Harmonic Motion", "Class 11", 4, "Medium",
            listOf("Simple Harmonic Motion Differential Equation", "Simple Pendulum Time Period", "Kinetic & Potential Energy in SHM", "Damped & Forced Oscillations"), 2),
        ChapterDetail("NEET", "Physics", "Waves & Acoustics", "Class 11", 4, "Medium",
            listOf("Transverse & Longitudinal Wave Equations", "Standing Waves in Open & Closed Organ Pipes", "Beat Frequency Phenomenon", "Doppler Effect in Sound"), 2),

        // --- CHEMISTRY ---
        ChapterDetail("NEET", "Chemistry", "Chemical Bonding & Molecular Structure", "Class 11", 9, "High",
            listOf("VSEPR Theory & Molecular Shapes", "Hybridization (sp, sp2, sp3, sp3d, sp3d2)", "Molecular Orbital Theory (Bond Order, Magnetism)", "Hydrogen Bonding & Dipole Moment"), 4),
        ChapterDetail("NEET", "Chemistry", "Organic Chemistry: Principles & Techniques (GOC)", "Class 11", 8, "High",
            listOf("IUPAC Nomenclature Rules", "Inductive, Electromeric & Resonance Effects", "Hyperconjugation & Carbocation Stability", "Structural & Stereo Isomerism"), 4),
        ChapterDetail("NEET", "Chemistry", "Coordination Compounds", "Class 12", 8, "High",
            listOf("Werner's Theory & Primary/Secondary Valence", "IUPAC Naming of Complexes", "Valence Bond Theory (VBT)", "Crystal Field Splitting in Octahedral/Tetrahedral"), 3),
        ChapterDetail("NEET", "Chemistry", "Chemical & Ionic Equilibrium", "Class 11", 7, "High",
            listOf("Law of Mass Action & Kp-Kc Relation", "Le Chatelier's Principle & Stress Response", "pH Calculations for Weak Acids & Bases", "Buffer Solutions & Solubility Product (Ksp)"), 3),
        ChapterDetail("NEET", "Chemistry", "Aldehydes, Ketones & Carboxylic Acids", "Class 12", 8, "High",
            listOf("Nucleophilic Addition Reactions", "Aldol & Cannizzaro Condensations", "Tollens' & Fehling's Reagents", "Acidity of Carboxylic Acids & Hell-Volhard Reaction"), 4),
        ChapterDetail("NEET", "Chemistry", "Thermodynamics & Thermochemistry", "Class 11", 7, "High",
            listOf("First Law: Work, Heat & Internal Energy", "Enthalpy of Reaction & Hess's Law", "Entropy & Second Law of Thermodynamics", "Gibbs Free Energy & Spontaneity Criteria"), 3),
        ChapterDetail("NEET", "Chemistry", "Electrochemistry", "Class 12", 7, "High",
            listOf("Galvanic Cells & Standard Hydrogen Electrode", "Nernst Equation & Cell Potential", "Kohlrausch's Law of Independent Migration", "Faraday's Laws of Electrolysis & Batteries"), 3),
        ChapterDetail("NEET", "Chemistry", "Solutions & Colligative Properties", "Class 12", 6, "Medium",
            listOf("Raoult's Law (Ideal & Non-ideal Solutions)", "Relative Lowering of Vapour Pressure", "Elevation in Boiling Point & Depression in Freezing", "Osmotic Pressure & Van't Hoff Factor"), 2),
        ChapterDetail("NEET", "Chemistry", "Chemical Kinetics", "Class 12", 6, "Medium",
            listOf("Rate of Reaction & Rate Law Expression", "Order vs Molecularity", "Integrated Rate Equations (Zero & First Order)", "Arrhenius Equation & Activation Energy"), 2),
        ChapterDetail("NEET", "Chemistry", "Hydrocarbons", "Class 11", 6, "High",
            listOf("Alkanes: Free Radical Halogenation", "Alkenes: Markownikoff & Anti-Markownikoff Addition", "Ozonolysis of Alkenes", "Benzene: Electrophilic Aromatic Substitution"), 3),
        ChapterDetail("NEET", "Chemistry", "Alcohols, Phenols & Ethers", "Class 12", 6, "Medium",
            listOf("Preparation & Lucas Test for Alcohols", "Kolbe's & Reimer-Tiemann Reactions of Phenol", "Williamson's Ether Synthesis", "Cleavage of Ethers by HI"), 2),
        ChapterDetail("NEET", "Chemistry", "Amines & Nitrogen Compounds", "Class 12", 6, "Medium",
            listOf("Basicity Order of Aliphatic & Aromatic Amines", "Hinsberg's Test for Distinction", "Hoffmann's Bromamide Degradation", "Diazonium Salt Synthetic Applications"), 2),
        ChapterDetail("NEET", "Chemistry", "d- and f-Block Elements", "Class 12", 6, "Medium",
            listOf("Lanthanoid & Actinoid Contraction", "Variable Oxidation States & Color Origin", "Magnetic Moment (Spin-only formula)", "KMnO4 and K2Cr2O7 Redox Reactions"), 2),
        ChapterDetail("NEET", "Chemistry", "Biomolecules", "Class 12", 5, "Medium",
            listOf("Monosaccharides: Glucose Structure & Mutarotation", "Amino Acids, Zwitterion & Peptide Linkage", "Primary to Quaternary Protein Structures", "DNA vs RNA & Denaturation"), 2),
        ChapterDetail("NEET", "Chemistry", "Periodic Classification of Elements", "Class 11", 5, "Core",
            listOf("Modern Periodic Table Trends", "Ionization Enthalpy & Electron Gain Enthalpy", "Electronegativity Scales (Pauling)", "Atomic, Ionic & Covalent Radii Variations"), 2),

        // --- BOTANY ---
        ChapterDetail("NEET", "Botany", "Genetics: Principles of Inheritance & Variation", "Class 12", 10, "High",
            listOf("Mendel's Monohybrid & Dihybrid Crosses", "Incomplete Dominance, Codominance & Multiple Alleles", "Chromosomal Theory of Inheritance", "Linkage, Recombination & Pedigree Charts"), 6),
        ChapterDetail("NEET", "Botany", "Molecular Basis of Inheritance", "Class 12", 10, "High",
            listOf("DNA Double Helix Structure & Packaging", "Meselson-Stahl Semiconservative Replication", "Transcription Unit & RNA Processing", "Genetic Code, Translation & Lac Operon"), 6),
        ChapterDetail("NEET", "Botany", "Photosynthesis in Higher Plants", "Class 11", 8, "High",
            listOf("Chloroplast Pigments & Action Spectra", "Light Reaction: Photosystem I & II (Z-Scheme)", "Cyclic & Non-Cyclic Photophosphorylation", "Calvin Cycle (C3) & Hatch-Slack Pathway (C4)"), 4),
        ChapterDetail("NEET", "Botany", "Respiration in Plants", "Class 11", 7, "High",
            listOf("Glycolysis Steps & ATP Accounting", "Fermentation Pathway", "Krebs Cycle / TCA Cycle Reactions", "Electron Transport System (ETS) & ATP Synthase"), 3),
        ChapterDetail("NEET", "Botany", "Cell Biology & Cell Cycle", "Class 11", 9, "High",
            listOf("Prokaryotic vs Eukaryotic Cell Structure", "Endomembrane System (ER, Golgi, Lysosomes)", "Mitochondria & Chloroplast Semi-Autonomous Nature", "Mitosis vs Meiosis & Crossing Over Stages"), 5),
        ChapterDetail("NEET", "Botany", "Sexual Reproduction in Flowering Plants", "Class 12", 8, "High",
            listOf("Microsporogenesis & Pollen Grain Wall", "Megasporogenesis & Embryo Sac 7-celled 8-nucleate", "Double Fertilization & Triple Fusion", "Endosperm Development & Apomixis"), 4),
        ChapterDetail("NEET", "Botany", "Ecosystem & Ecological Pyramids", "Class 12", 8, "High",
            listOf("Productivity: Primary & Secondary (GPP, NPP)", "Decomposition Process Stages", "Pyramid of Numbers, Biomass & Energy", "Ecological Succession (Hydrarch, Xerarch)"), 4),
        ChapterDetail("NEET", "Botany", "Plant Growth & Regulators (Phytohormones)", "Class 11", 5, "Medium",
            listOf("Auxin, Gibberellin & Cytokinin Actions", "Ethylene & Abscisic Acid (Stress Hormone)", "Photoperiodism & Critical Day Length", "Vernalization & Seed Dormancy"), 2),
        ChapterDetail("NEET", "Botany", "Morphology & Anatomy of Flowering Plants", "Class 11", 7, "Medium",
            listOf("Root, Stem & Leaf Modifications", "Inflorescence Types (Racemose, Cymose)", "Dicot vs Monocot Stem/Root Anatomy", "Secondary Growth & Cambial Ring"), 3),

        // --- ZOOLOGY ---
        ChapterDetail("NEET", "Zoology", "Human Physiology: Neural Control & Coordination", "Class 11", 9, "High",
            listOf("Neuron Structure & Resting Membrane Potential", "Action Potential Generation & Conduction", "Synaptic Transmission (Electrical & Chemical)", "Human Brain Parts & Reflex Arc"), 4),
        ChapterDetail("NEET", "Zoology", "Human Physiology: Chemical Coordination (Endocrine)", "Class 11", 8, "High",
            listOf("Hypothalamus & Pituitary Hormones Axis", "Thyroid, Parathyroid & Adrenal Glands", "Insulin, Glucagon & Diabetes Mellitus", "Mechanism of Hormone Action (Peptide vs Steroid)"), 4),
        ChapterDetail("NEET", "Zoology", "Human Reproduction & Embryonic Development", "Class 12", 8, "High",
            listOf("Male & Female Reproductive System Anatomy", "Spermatogenesis & Oogenesis Differences", "Hormonal Regulation of Menstrual Cycle", "Fertilization, Cleavage, Blastocyst & Implantation"), 4),
        ChapterDetail("NEET", "Zoology", "Biotechnology: Principles & Processes", "Class 12", 9, "High",
            listOf("Restriction Endonucleases (Molecular Scissors)", "Cloning Vectors: pBR322, Plasmids & Competent Host", "Polymerase Chain Reaction (PCR) Steps", "Agarose Gel Electrophoresis & Bioreactors"), 5),
        ChapterDetail("NEET", "Zoology", "Biotechnology & Its Applications in Medicine", "Class 12", 7, "High",
            listOf("Genetically Engineered Insulin (Humulin)", "Gene Therapy for ADA Deficiency", "RNA Interference (RNAi) in Pest Resistance", "Bt Crops & Transgenic Animal Models"), 3),
        ChapterDetail("NEET", "Zoology", "Human Physiology: Circulation & Body Fluids", "Class 11", 7, "High",
            listOf("Blood Composition, ABO & Rh Incompatibility", "Human Heart Anatomy & Conducting System (SA node)", "Cardiac Cycle, Cardiac Output & Blood Pressure", "Electrocardiogram (ECG) Waves Interpretation"), 3),
        ChapterDetail("NEET", "Zoology", "Human Physiology: Excretory Products & Elimination", "Class 11", 7, "High",
            listOf("Nephron Structure: Cortical vs Juxtamedullary", "Urine Formation: Filtration, Reabsorption, Secretion", "Counter-Current Multiplier Mechanism in Henle's Loop", "Regulation by ADH, RAAS & ANF"), 3),
        ChapterDetail("NEET", "Zoology", "Animal Kingdom: Classification & Phyla", "Class 11", 7, "Medium",
            listOf("Levels of Organization, Symmetry & Coelom Types", "Porifera, Cnidaria, Platyhelminthes & Annelida", "Arthropoda (Largest Phylum) & Mollusca Features", "Chordata Subphyla & Vertebrata Classes"), 3),
        ChapterDetail("NEET", "Zoology", "Reproductive Health & Infertility Solutions", "Class 12", 6, "Medium",
            listOf("Contraceptive Methods (Barrier, IUD, Hormonal)", "Medical Termination of Pregnancy (MTP Act)", "Sexually Transmitted Infections (STIs)", "Assisted Reproductive Technologies (IVF, ZIFT, ICSI)"), 2),
        ChapterDetail("NEET", "Zoology", "Human Health & Disease", "Class 12", 7, "High",
            listOf("Infectious Diseases (Typhoid, Pneumonia, Malaria Lifecycle)", "Innate vs Acquired Immunity (B-cells & T-cells)", "Antibody Structure & Types (IgG, IgA, IgM, IgE)", "Cancer Characteristics & AIDS Mechanism"), 3),
        ChapterDetail("NEET", "Zoology", "Evolution & Darwinian Theories", "Class 12", 6, "Medium",
            listOf("Origin of Life: Miller-Urey Experiment", "Homologous vs Analogous Organs (Divergent/Convergent)", "Natural Selection & Industrial Melanism", "Hardy-Weinberg Equilibrium Principle & Human Evolution"), 2)
    )

    // =========================================================================
    // 2. JEE MAINS SYLLABUS & WEIGHTAGE (Physics, Chemistry, Mathematics)
    // =========================================================================
    val jeeMainSyllabus = listOf(
        // --- MATHEMATICS ---
        ChapterDetail("JEE", "Mathematics", "Definite Integrals & Area Under Curves", "Class 12", 10, "High",
            listOf("King's & Queen's Properties of Definite Integrals", "Leibnitz Rule of Differentiation Under Integral", "Area Bounded Between Curves", "Periodic Functions Integration"), 3),
        ChapterDetail("JEE", "Mathematics", "Differential Equations", "Class 12", 8, "High",
            listOf("Order & Degree of Differential Equations", "Variable Separable & Homogeneous Equations", "First Order Linear Differential Equation (IF)", "Exact & Bernoulli Differential Equations"), 2),
        ChapterDetail("JEE", "Mathematics", "Vector Algebra & 3D Geometry", "Class 12", 12, "High",
            listOf("Dot & Cross Product Identities", "Scalar & Vector Triple Products", "Line in 3D: Symmetric & Vector Form", "Shortest Distance Between Skew Lines"), 3),
        ChapterDetail("JEE", "Mathematics", "Coordinate Geometry: Conic Sections (Parabola, Ellipse, Hyperbola)", "Class 11", 12, "High",
            listOf("Standard Equations & Focal Properties", "Tangents, Normals & Chord of Contact", "Condition for Tangency (c = a/m or c^2 = a^2 m^2 + b^2)", "Eccentricity & Directrix Relations"), 3),
        ChapterDetail("JEE", "Mathematics", "Matrices & System of Linear Equations", "Class 12", 8, "High",
            listOf("Matrix Multiplication Properties & Transpose", "Adjoint, Inverse & Cayley-Hamilton Theorem", "Cramer's Rule & Consistency of Equations", "Homogeneous vs Non-homogeneous Systems"), 2),
        ChapterDetail("JEE", "Mathematics", "Limits, Continuity & Differentiability", "Class 12", 8, "High",
            listOf("Standard Limits (e^x, ln, trigonometric)", "L'Hopital's Rule for Indeterminate Forms", "Continuity at a Point & Intermediate Value Theorem", "Differentiability & Corner Points"), 2),
        ChapterDetail("JEE", "Mathematics", "Application of Derivatives (Max-Min & Monotonicity)", "Class 12", 8, "High",
            listOf("Increasing & Decreasing Function Tests", "Maxima & Minima (First & Second Derivative Tests)", "Rolle's & Lagrange's Mean Value Theorems", "Tangent & Normal Equations to Curve"), 2),
        ChapterDetail("JEE", "Mathematics", "Complex Numbers & Quadratic Equations", "Class 11", 8, "High",
            listOf("Euler Form & De Moivre's Theorem", "Cube Roots of Unity (1, ω, ω^2)", "Geometry of Complex Numbers (Circles, Straight Lines)", "Location of Roots in Quadratic Equations"), 2),
        ChapterDetail("JEE", "Mathematics", "Permutations, Combinations & Probability", "Class 11/12", 9, "High",
            listOf("Fundamental Counting Principle", "Selection & Distribution of Alike/Distinct Objects", "Conditional Probability & Bayes' Theorem", "Binomial Distribution & Expectation"), 3),
        ChapterDetail("JEE", "Mathematics", "Sequences & Series (AP, GP, Telescoping)", "Class 11", 6, "Medium",
            listOf("nth Term & Sum of AP and GP", "Arithmetic-Geometric Progression (AGP)", "Sum to n Terms of Special Series (Σn, Σn^2, Σn^3)", "AM-GM-HM Inequality Applications"), 2),
        ChapterDetail("JEE", "Mathematics", "Binomial Theorem", "Class 11", 5, "Medium",
            listOf("General & Middle Term in (x + y)^n", "Greatest Term in Binomial Expansion", "Sum of Binomial Coefficients Series", "Multinomial Theorem Basics"), 1),
        ChapterDetail("JEE", "Mathematics", "Trigonometric Equations & Inverse Trigonometric Functions", "Class 11/12", 6, "Medium",
            listOf("General Solutions of Trigonometric Equations", "ITF Domain, Range & Principal Values", "Addition & Subtraction of Inverse Functions", "Trigonometric Identities & Transformations"), 2),

        // --- PHYSICS ---
        ChapterDetail("JEE", "Physics", "Rotational Dynamics & Rigid Body Mechanics", "Class 11", 10, "High",
            listOf("Moment of Inertia of Symmetrical Bodies", "Torque = I * α & Angular Momentum Conservation", "Rolling Without Slipping Dynamics", "Instantaneous Center of Rotation"), 3),
        ChapterDetail("JEE", "Physics", "Current Electricity & Circuit Networks", "Class 12", 9, "High",
            listOf("Kirchhoff's Rules for Complex Meshes", "Symmetry in Resistor Networks (Delta-Star)", "RC Circuit Charging & Discharging Transient", "Meter Bridge & Galvanometer Conversions"), 3),
        ChapterDetail("JEE", "Physics", "Electrostatics & Gauss Law", "Class 12", 8, "High",
            listOf("Field & Potential Due to Continuous Charge Distributions", "Gauss Law for Spherical, Cylindrical & Planar Symmetry", "Energy Stored in Electrostatic Field", "Conductor Cavity & Electrostatic Shielding"), 2),
        ChapterDetail("JEE", "Physics", "Magnetic Field & Electromagnetic Induction", "Class 12", 9, "High",
            listOf("Ampere's Law for Thick Cylinders & Solenoids", "Motional EMF in Rotating Conductors", "Inductor Transient (L-R Circuit)", "L-C Oscillations & Alternating Current"), 3),
        ChapterDetail("JEE", "Physics", "Thermodynamics & Kinetic Theory", "Class 11", 9, "High",
            listOf("First Law: Work in P-V Diagrams", "Adiabatic Process Equations (PV^γ = const)", "Degrees of Freedom & Molar Heat Capacities", "Carnot Engine & Entropy Concept"), 3),
        ChapterDetail("JEE", "Physics", "Modern Physics: Atoms, Nuclei & Photoelectric Effect", "Class 12", 10, "High",
            listOf("Einstein's Photoelectric Equation", "de-Broglie Wavelength in Various Fields", "Bohr Model for Hydrogen-like Atoms", "Radioactive Decay Law & Q-Value of Nuclear Reaction"), 3),
        ChapterDetail("JEE", "Physics", "Wave Optics: Interference & Diffraction", "Class 12", 7, "Medium",
            listOf("Coherent Sources & Fringe Width in YDSE", "YDSE with Thin Transparent Slab", "Single Slit Fraunhofer Diffraction", "Polarization by Brewster's Law"), 2),
        ChapterDetail("JEE", "Physics", "Work, Power & Energy", "Class 11", 6, "Medium",
            listOf("Work Done by Variable Force", "Potential Energy Curve & Equilibrium Types", "Elastic & Inelastic 2D Collisions", "Vertical Circular Motion Critical Speeds"), 2),
        ChapterDetail("JEE", "Physics", "Gravitation & Planetary Orbits", "Class 11", 5, "Medium",
            listOf("Gravitational Field & Potential Calculation", "Kepler's Laws & Planetary Elliptical Orbits", "Binding Energy of Satellite & Energy Conservation"), 2),
        ChapterDetail("JEE", "Physics", "Simple Harmonic Motion & Oscillations", "Class 11", 6, "Medium",
            listOf("SHM Equation: Acceleration & Velocity", "Spring-Block Combinations & Equivalent k", "Physical Pendulum Time Period", "Damped Oscillation Energy Decay"), 2),

        // --- CHEMISTRY ---
        ChapterDetail("JEE", "Chemistry", "Chemical Bonding & Molecular Orbital Theory", "Class 11", 10, "High",
            listOf("VSEPR Theory & Geometrical Distortions", "Hybridization & Steric Number", "MOT Energy Diagrams for Hetero/Homo Diatomics", "Back Bonding & Intermolecular Interactions"), 3),
        ChapterDetail("JEE", "Chemistry", "Chemical Thermodynamics & Thermochemistry", "Class 11", 8, "High",
            listOf("State Functions: U, H, S, G", "Work Done in Reversible vs Irreversible Expansion", "Gibbs Helmholtz Equation & Temperature Dependence", "Bond Enthalpy Calculations"), 2),
        ChapterDetail("JEE", "Chemistry", "Chemical Kinetics & Nuclear Chemistry", "Class 12", 8, "High",
            listOf("Differential & Integrated Rate Laws", "Pseudo First Order Reactions", "Temperature Dependence & Arrhenius Parameters", "Collision Theory & Reaction Coordinate Diagrams"), 2),
        ChapterDetail("JEE", "Chemistry", "Electrochemistry & Redox Systems", "Class 12", 8, "High",
            listOf("Electrochemical Series Applications", "Nernst Equation Calculations at Equilibrium", "Conductance: Specific, Equivalent, Molar", "Electrolysis & Quantitative Calculations"), 2),
        ChapterDetail("JEE", "Chemistry", "Coordination Chemistry & Isomerism", "Class 12", 9, "High",
            listOf("Structural & Stereoisomerism in Complexes", "Crystal Field Theory (CFSE Calculation)", "Spectrochemical Series & Jahn-Teller Effect Basics", "Stability Constants & Chelate Effect"), 3),
        ChapterDetail("JEE", "Chemistry", "Organic Chemistry: Carbonyl Compounds (Aldehydes & Ketones)", "Class 12", 9, "High",
            listOf("Nucleophilic Addition Reactions Mechanism", "Aldol, Cannizzaro, Perkins Reactions", "Oxidation with Tollens, Fehling, Iodoform Test", "Carboxylic Acids Acidity & Decarboxylation"), 3),
        ChapterDetail("JEE", "Chemistry", "Aromatic Compounds & Electrophilic Substitution", "Class 11/12", 7, "High",
            listOf("Aromaticity: Huckel's 4n+2 Rule", "Nitration, Halogenation, Sulphonation", "Friedel-Crafts Alkylation & Acylation (Rearrangements)", "Activating vs Deactivating Directing Groups"), 2),
        ChapterDetail("JEE", "Chemistry", "Equilibrium: Chemical & Ionic", "Class 11", 8, "High",
            listOf("Equilibrium Constants Kp, Kc, Kx relations", "Le Chatelier's Principle Applications", "Salt Hydrolysis & Buffer Solution Equations", "Sparingly Soluble Salts Precipitation Conditions"), 2)
    )

    // =========================================================================
    // 3. UPSC CDS SYLLABUS & WEIGHTAGE (Maths, English, General Knowledge)
    // =========================================================================
    val upscCdsSyllabus = listOf(
        // --- ELEMENTARY MATHEMATICS ---
        ChapterDetail("UPSC_CDS", "Elementary Mathematics", "Arithmetic: Number System & Divisibility", "General", 12, "High",
            listOf("Natural Numbers, Integers, Rational Numbers", "Divisibility Rules & Remainder Theorems", "Prime Factorization, HCF & LCM", "Decimal Fractions & Unit Digit Patterns"), 8),
        ChapterDetail("UPSC_CDS", "Elementary Mathematics", "Commercial Maths (Percentage, Profit & Loss, SI/CI)", "General", 14, "High",
            listOf("Percentage Conversions & Net Change", "Profit, Loss & Successive Discounts", "Simple & Compound Interest with Compounding Periods", "Partnership & Ratio-Proportion Applications"), 10),
        ChapterDetail("UPSC_CDS", "Elementary Mathematics", "Time & Work, Pipes & Cisterns", "General", 8, "High",
            listOf("Work Efficiency & Days Ratio", "Men, Days, Hours Formula", "Alternating Work Problems", "Inlet & Outlet Pipe Combinations"), 6),
        ChapterDetail("UPSC_CDS", "Elementary Mathematics", "Time, Speed & Distance (Trains & Boats)", "General", 8, "High",
            listOf("Relative Speed Concept", "Trains Crossing Pole and Platform", "Boats in Upstream & Downstream River", "Average Speed Formulas"), 6),
        ChapterDetail("UPSC_CDS", "Elementary Mathematics", "Trigonometry & Heights and Distances", "General", 14, "High",
            listOf("Trigonometric Identities (sin^2 + cos^2 = 1, etc.)", "Values for 0°, 30°, 45°, 60°, 90°", "Angles of Elevation & Depression", "Heights of Towers & River Width Problems"), 10),
        ChapterDetail("UPSC_CDS", "Elementary Mathematics", "Geometry: Lines, Angles, Triangles & Circles", "General", 16, "High",
            listOf("Properties of Parallel Lines & Angles", "Triangle Congruence & Similarity Theorems", "Pythagoras Theorem & Apollonius Theorem", "Chords, Secants & Tangents to Circle"), 12),
        ChapterDetail("UPSC_CDS", "Elementary Mathematics", "Mensuration 2D & 3D (Area & Volume)", "General", 14, "High",
            listOf("Area & Perimeter of Triangles, Rectangles, Trapeziums", "Surface Area & Volume of Cubes, Cylinders, Cones, Spheres", "Melting & Reshaping Solids Problems"), 10),
        ChapterDetail("UPSC_CDS", "Elementary Mathematics", "Statistics & Data Interpretation", "General", 6, "Medium",
            listOf("Mean, Median, Mode Calculations", "Frequency Polygons & Histograms", "Bar Diagrams & Pie Chart Data Analysis"), 4),

        // --- ENGLISH ---
        ChapterDetail("UPSC_CDS", "English", "Spotting Errors in Sentences", "General", 20, "High",
            listOf("Subject-Verb Agreement Rules", "Correct Use of Prepositions & Conjunctions", "Tense Consistency & Conditionals", "Pronoun Antecedent Agreement"), 15),
        ChapterDetail("UPSC_CDS", "English", "Reading Comprehension Passages", "General", 20, "High",
            listOf("Direct Fact Identification", "Inference & Author's Tone Evaluation", "Central Theme & Title Selection", "Vocabulary in Passage Context"), 15),
        ChapterDetail("UPSC_CDS", "English", "Sentence Rearrangement (Para Jumbles)", "General", 15, "High",
            listOf("Opening Sentence Identification", "Chronological & Logical Connectors", "Pronoun Clues for Pairing", "Closing Sentence Placement"), 10),
        ChapterDetail("UPSC_CDS", "English", "Synonyms & Antonyms", "General", 15, "High",
            listOf("High-Frequency Defense & Academic Words", "Contextual Vocabulary Usage", "Prefix & Suffix Clues", "Word Elimination Technique"), 10),
        ChapterDetail("UPSC_CDS", "English", "Idioms, Phrases & One Word Substitution", "General", 15, "Medium",
            listOf("Popular Idiomatic Expressions", "Phrasal Verbs with Look, Give, Put, Break", "Standard One-Word Substitutions"), 10),
        ChapterDetail("UPSC_CDS", "English", "Fill in the Blanks & Cloze Test", "General", 15, "Medium",
            listOf("Preposition Fillers", "Collocations & Grammatical Articles", "Passage Coherence & Blank Evaluation"), 10),

        // --- GENERAL KNOWLEDGE ---
        ChapterDetail("UPSC_CDS", "General Knowledge", "Indian Polity & Constitution", "General", 20, "High",
            listOf("Preamble, Fundamental Rights & Duties", "Directive Principles of State Policy (DPSP)", "President, Prime Minister & Parliament Powers", "Supreme Court & High Court Judicial Review"), 15),
        ChapterDetail("UPSC_CDS", "General Knowledge", "Indian History: Freedom Struggle & Modern India", "General", 18, "High",
            listOf("1857 Revolt Causes & Consequences", "Indian National Congress & Moderate/Extremist Eras", "Gandhian Movements (Non-Cooperation, Civil Disobedience, Quit India)", "Constitutional Acts (1909, 1919, 1935)"), 14),
        ChapterDetail("UPSC_CDS", "General Knowledge", "Geography: Physical & Indian Geography", "General", 18, "High",
            listOf("Earth Structure, Latitudes & Longitudes", "Indian River Systems (Himalayan vs Peninsular)", "Monsoons, Climate & Soil Types in India", "Major Minerals, Ports & Industrial Centers"), 14),
        ChapterDetail("UPSC_CDS", "General Knowledge", "General Science: Physics, Chemistry & Biology", "General", 24, "High",
            listOf("Physics: Optics, Motion, Sound & Electricity Daily Applications", "Chemistry: Acids, Bases, Salts, Metals & Everyday Compounds", "Biology: Human Body Systems, Diseases & Plant Nutrition"), 18),
        ChapterDetail("UPSC_CDS", "General Knowledge", "Defense Forces, National Security & Current Affairs", "General", 20, "High",
            listOf("Indian Armed Forces Commands & Ranks", "Recent Defense Acquisitions & Joint Military Exercises", "National & International Current Affairs (Past 6 Months)", "Important Government Schemes & International Summits"), 15)
    )

    // =========================================================================
    // 4. SSC CGL SYLLABUS & WEIGHTAGE (Quant, Reasoning, English, GA)
    // =========================================================================
    val sscCglSyllabus = listOf(
        // --- QUANTITATIVE APTITUDE ---
        ChapterDetail("SSC_CGL", "Quantitative Aptitude", "Arithmetic: Ratio, Percentage & Profit/Loss", "General", 18, "High",
            listOf("Direct & Inverse Ratios, Mixtures & Alligation", "Percentage Increase/Decrease & Successive Changes", "Marked Price, Discount, Cost & Selling Price Equations"), 6),
        ChapterDetail("SSC_CGL", "Quantitative Aptitude", "Advanced Maths: Geometry (Triangles, Circles)", "General", 16, "High",
            listOf("Properties of Similar Triangles", "Centroid, Incenter, Circumcenter & Orthocenter", "Tangent Theorems & Chord Intersections", "Cyclic Quadrilaterals"), 5),
        ChapterDetail("SSC_CGL", "Quantitative Aptitude", "Advanced Maths: Trigonometry & Heights", "General", 14, "High",
            listOf("Trigonometric Identities (sin, cos, tan, cot, sec, cosec)", "Complementary Angle Relations (90 - θ)", "Heights and Distances with Multiple Elevation Angles"), 4),
        ChapterDetail("SSC_CGL", "Quantitative Aptitude", "Advanced Maths: Algebra & Polynomials", "General", 14, "High",
            listOf("Algebraic Identities: (a+b+c)^2, a^3+b^3+c^3-3abc", "Linear & Quadratic Equations", "Symmetric Expressions & Value Substitution Technique"), 4),
        ChapterDetail("SSC_CGL", "Quantitative Aptitude", "Mensuration 2D & 3D", "General", 12, "Medium",
            listOf("Area of Polygons & Shaded Regions", "Volumes & Total Surface Area of Cylinders, Cones, Spheres, Prisms"), 3),
        ChapterDetail("SSC_CGL", "Quantitative Aptitude", "Time, Work, Speed, Distance & Boats", "General", 14, "High",
            listOf("Work Efficiency & Wages Sharing", "Train Crossing Platform & Other Trains", "Stream Velocity & Downstream/Upstream Times"), 4),
        ChapterDetail("SSC_CGL", "Quantitative Aptitude", "Data Interpretation (DI)", "General", 12, "High",
            listOf("Bar Graph, Pie Chart & Table Interpretation", "Percentage & Ratio Based DI Questions"), 4),

        // --- GENERAL INTELLIGENCE & REASONING ---
        ChapterDetail("SSC_CGL", "Reasoning", "Analogies & Classification (Odd One Out)", "General", 16, "High",
            listOf("Semantic, Symbolic & Number Analogies", "Letter Cluster Classifications", "GK Based Analogies"), 4),
        ChapterDetail("SSC_CGL", "Reasoning", "Coding-Decoding & Letter Series", "General", 16, "High",
            listOf("Letter Shift & Reverse Alphabet Codes", "Number & Symbol Coding", "Matrix & Direct Substitution Coding"), 4),
        ChapterDetail("SSC_CGL", "Reasoning", "Syllogisms & Venn Diagrams", "General", 14, "High",
            listOf("Statements & Conclusions (Some, All, No, At least)", "Venn Diagrams Representation of Classes"), 4),
        ChapterDetail("SSC_CGL", "Reasoning", "Blood Relations & Direction Sense", "General", 14, "High",
            listOf("Coded Blood Relations (A + B means A is father of B)", "Family Tree Diagramming", "Pythagoras Theorem in Distance & Final Direction"), 3),
        ChapterDetail("SSC_CGL", "Reasoning", "Non-Verbal Reasoning: Mirror Images & Paper Folding", "General", 16, "High",
            listOf("Mirror & Water Image Transformations", "Paper Folding & Punched Hole Patterns", "Figure Completion & Embedded Hidden Figures"), 4),
        ChapterDetail("SSC_CGL", "Reasoning", "Puzzles, Seating Arrangement & Order Ranking", "General", 14, "Medium",
            listOf("Linear & Circular Seating Facing Center/Out", "Top/Bottom Ranking Calculations"), 3),
        ChapterDetail("SSC_CGL", "Reasoning", "Mathematical Operations & Symbol Substitutions", "General", 10, "Medium",
            listOf("BODMAS Rule with Swapped Signs", "Balancing Equations by Interchanging Numbers"), 3),

        // --- ENGLISH COMPREHENSION ---
        ChapterDetail("SSC_CGL", "English", "Grammar: Spotting Errors & Sentence Improvement", "General", 22, "High",
            listOf("Subject-Verb Concord", "Conditionals (If + had + V3, would have + V3)", "Preposition Misuse & Redundancies", "Tense Consistency"), 6),
        ChapterDetail("SSC_CGL", "English", "Active / Passive Voice & Direct / Indirect Speech", "General", 18, "High",
            listOf("Voice Transformation for Imperative & Interrogative Sentences", "Narration Changes: Tense, Pronoun & Time Shifting"), 5),
        ChapterDetail("SSC_CGL", "English", "Vocabulary: Synonyms, Antonyms & One Word Substitution", "General", 20, "High",
            listOf("Common SSC Repeated Root Words", "One Word for Specialist Doctors, Phobias, Governments", "Idiomatic Phrases & Catchy Expressions"), 5),
        ChapterDetail("SSC_CGL", "English", "Cloze Test & Reading Comprehension", "General", 24, "High",
            listOf("Passage Blank Filling with Grammar/Vocab Context", "Short Narrative Passages Inference"), 6),
        ChapterDetail("SSC_CGL", "English", "Spelling Errors & Sentence Jumbles", "General", 16, "Medium",
            listOf("Common Misspelled Words (Double letters)", "4-Sentence Para Jumble Sequences"), 4),

        // --- GENERAL AWARENESS ---
        ChapterDetail("SSC_CGL", "General Awareness", "Indian Polity & Governance", "General", 20, "High",
            listOf("Constitutional Articles & Amendments (42nd, 44th, 73rd, 86th, 101st)", "Fundamental Rights (Articles 14 to 32)", "Parliament: Lok Sabha & Rajya Sabha Procedures", "Constitutional & Non-Constitutional Bodies (CAG, Election Commission, NITI Aayog)"), 5),
        ChapterDetail("SSC_CGL", "General Awareness", "Indian History: Ancient, Medieval & Modern", "General", 20, "High",
            listOf("Indus Valley Civilization & Vedic Period", "Mughal Empire & Delhi Sultanate Dynasties", "Buddhism & Jainism Teachings", "British Acts & Freedom Struggle Milestones"), 5),
        ChapterDetail("SSC_CGL", "General Awareness", "Static GK: Art, Culture, Dances, Festivals & Sports", "General", 22, "High",
            listOf("Classical & Folk Dances of Indian States", "Major National Festivals & Fairs", "UNESCO World Heritage Sites in India", "Famous Temples, Monuments & Musical Instruments"), 6),
        ChapterDetail("SSC_CGL", "General Awareness", "Geography & Environment", "General", 16, "Medium",
            listOf("Major Indian Rivers, Dams & Waterfalls", "National Parks, Biosphere Reserves & Wildlife Sanctuaries", "Mountain Passes, Ranges & Peaks"), 4),
        ChapterDetail("SSC_CGL", "General Awareness", "General Science (Physics, Chemistry, Biology)", "General", 14, "Medium",
            listOf("Vitamins, Diseases & Nutrition", "Common Chemical Names (Plaster of Paris, Baking Soda)", "Physical Units & Scientific Inventions"), 3),
        ChapterDetail("SSC_CGL", "General Awareness", "Current Affairs & Economic Schemes", "General", 18, "High",
            listOf("Central Government Welfare Schemes", "Awards, Honours & Nobel Prizes", "Important Appointments, Governors & Chief Ministers", "Major Sports Tournaments (Olympics, World Cups)"), 4)
    )
}
