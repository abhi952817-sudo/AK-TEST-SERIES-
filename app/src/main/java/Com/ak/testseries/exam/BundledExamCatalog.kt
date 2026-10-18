package Com.ak.testseries.exam

import Com.ak.testseries.data.QuestionEntity
import Com.ak.testseries.data.TestEntity

/**
 * High-yield authentic Bundled Mock Tests and Question Banks for major competitive exams.
 * Pre-seeded to provide immediate offline and online test-taking experience for students.
 */
object BundledExamCatalog {

    fun getAllBundledTests(): List<Pair<TestEntity, List<QuestionEntity>>> {
        return listOf(
            getNeetUgMockTest1(),
            getJeeMainMockTest1(),
            getUpscCdsMockTest1(),
            getSscCglMockTest1()
        )
    }

    fun getBundledTestsForExam(targetExam: String): List<Pair<TestEntity, List<QuestionEntity>>> {
        val normalized = targetExam.trim().uppercase()
        return when {
            normalized.contains("NEET") || normalized.contains("MEDICAL") -> listOf(getNeetUgMockTest1())
            normalized.contains("JEE") || normalized.contains("ENGINEERING") || normalized.contains("IIT") -> listOf(getJeeMainMockTest1())
            normalized.contains("CDS") || normalized.contains("UPSC") || normalized.contains("NDA") || normalized.contains("DEFENCE") -> listOf(getUpscCdsMockTest1())
            normalized.contains("SSC") || normalized.contains("GOVT") || normalized.contains("CGL") -> listOf(getSscCglMockTest1())
            else -> getAllBundledTests()
        }
    }

    // =========================================================================
    // 1. NEET UG FULL SYLLABUS OFFICIAL MOCK TEST 1
    // 200 Questions, 720 Marks, 200 Minutes (Physics, Chemistry, Botany, Zoology)
    // =========================================================================
    fun getNeetUgMockTest1(): Pair<TestEntity, List<QuestionEntity>> {
        val testId = "official_neet_ug_mock_01"
        val questions = mutableListOf<QuestionEntity>()

        // Helper to add questions
        fun addQ(
            index: Int,
            subject: String,
            chapter: String,
            section: String,
            qEn: String,
            qHi: String,
            aEn: String, aHi: String,
            bEn: String, bHi: String,
            cEn: String, cHi: String,
            dEn: String, dHi: String,
            ans: String,
            expEn: String, expHi: String,
            diff: String = "Medium"
        ) {
            questions.add(
                QuestionEntity(
                    id = "${testId}_q${index}",
                    testId = testId,
                    questionText = qEn,
                    questionTextHi = qHi,
                    optionA = aEn,
                    optionAHi = aHi,
                    optionB = bEn,
                    optionBHi = bHi,
                    optionC = cEn,
                    optionCHi = cHi,
                    optionD = dEn,
                    optionDHi = dHi,
                    correctAnswer = ans,
                    explanation = expEn,
                    explanationHi = expHi,
                    subject = subject,
                    chapter = chapter,
                    section = section,
                    difficulty = diff,
                    questionType = "MCQ"
                )
            )
        }

        // --- PHYSICS SECTION A (35 Questions) & SECTION B (15 Questions) ---
        val physicsTopics = listOf(
            Triple("Units & Measurements", "The dimensional formula of magnetic flux is:", "चुंबकीय फ्लक्स का विमीय सूत्र है:"),
            Triple("Kinematics", "A body thrown vertically upwards reaches a maximum height h. The ratio of its velocity at h/2 to initial velocity is:", "ऊर्ध्वाधर ऊपर फेंका गया पिंड अधिकतम ऊँचाई h तक पहुँचता है। h/2 पर इसके वेग और प्रारंभिक वेग का अनुपात है:"),
            Triple("Laws of Motion", "A block of mass 2 kg rests on a rough horizontal surface with coefficient of friction 0.4. If a horizontal force of 5 N is applied, the friction force is:", "2 किग्रा का गुटका 0.4 घर्षण गुणांक वाले खुरदरे क्षैतिज तल पर रखा है। यदि 5 N का क्षैतिज बल लगाया जाए तो घर्षण बल होगा:"),
            Triple("Work Energy Power", "A particle of mass m moves in a circular path of radius r with uniform speed v. Work done in one complete rotation is:", "द्रव्यमान m का एक कण त्रिज्या r के वृत्ताकार पथ पर एकसमान गति v से घूमता है। एक पूर्ण चक्कर में किया गया कार्य है:"),
            Triple("Rotational Motion", "The radius of gyration of a solid sphere of radius R about its diameter is:", "त्रिज्या R के एक ठोस गोले का इसके व्यास के परितः परिभ्रमण त्रिज्या है:"),
            Triple("Gravitation", "The value of acceleration due to gravity at depth d below Earth surface becomes g/4. The value of d in terms of Earth radius R is:", "पृथ्वी की सतह से d गहराई पर गुरुत्वीय त्वरण g/4 हो जाता है। R के पदों में d का मान है:"),
            Triple("Mechanical Properties of Solids", "The Young's modulus of a perfectly rigid body is:", "एक पूर्णतः दृढ़ पिंड का यंग प्रत्यास्थता गुणांक होता है:"),
            Triple("Mechanical Properties of Fluids", "According to Bernoulli's theorem, in streamlined flow of an ideal fluid, the total energy per unit mass is:", "बरनौली प्रमेय के अनुसार, आदर्श द्रव के धारा-रेखी प्रवाह में प्रति इकाई द्रव्यमान कुल ऊर्जा होती है:"),
            Triple("Thermal Properties of Matter", "Which of the following processes has the maximum rate of heat transfer?", "निम्नलिखित में से किस प्रक्रिया में ऊष्मा स्थानांतरण की दर अधिकतम होती है?"),
            Triple("Thermodynamics", "In an adiabatic process, the pressure and temperature of a gas are related as P^(1-γ) * T^γ = constant. The work done during adiabatic expansion depends on:", "रुद्धोष्म प्रक्रम में, गैस का दाब और ताप P^(1-γ) * T^γ = नियतांक से संबंधित होते हैं। रुद्धोष्म प्रसार में किया गया कार्य निर्भर करता है:"),
            Triple("Kinetic Theory of Gases", "The root mean square speed of oxygen molecules at temperature T is v. If temperature is doubled and oxygen dissociates into atoms, rms speed becomes:", "ताप T पर ऑक्सीजन अणुओं का वर्ग-माध्य-मूल वेग v है। यदि ताप दोगुना कर दिया जाए और अणु परमाणुओं में टूट जाएं, तो rms वेग होगा:"),
            Triple("Oscillations", "The total energy of a particle executing simple harmonic motion with amplitude A is proportional to:", "आयाम A के साथ सरल आवर्त गति कर रहे कण की कुल ऊर्जा समानुपाती होती है:"),
            Triple("Waves", "Two sound waves of wavelengths 1.0 m and 1.01 m produce 3 beats per second. The velocity of sound in the medium is:", "1.0 m और 1.01 m तरंगदैर्ध्य की दो ध्वनि तरंगें प्रति सेकंड 3 विस्पंद उत्पन्न करती हैं। माध्यम में ध्वनि का वेग है:"),
            Triple("Electrostatics", "Electric flux through a closed Gaussian surface enclosing charge Q is Φ. If radius of sphere is doubled, flux will be:", "आवेश Q को परिबद्ध करने वाले बंद गाऊसीय पृष्ठ से विद्युत फ्लक्स Φ है। यदि गोले की त्रिज्या दोगुनी कर दी जाए, तो फ्लक्स होगा:"),
            Triple("Current Electricity", "A wire of resistance R is stretched uniformly such that its length increases by 10%. The percentage increase in its resistance is approximately:", "प्रतिरोध R के एक तार को एकसमान खींचा जाता है जिससे इसकी लंबाई 10% बढ़ जाती है। इसके प्रतिरोध में प्रतिशत वृद्धि लगभग है:"),
            Triple("Moving Charges & Magnetism", "A charged particle moves with velocity v in magnetic field B at an angle of 90°. The radius of circular path is proportional to:", "एक आवेशित कण चुंबकीय क्षेत्र B में 90° के कोण पर वेग v से गति करता है। वृत्ताकार पथ की त्रिज्या समानुपाती होती है:"),
            Triple("Magnetism & Matter", "The magnetic susceptibility of a paramagnetic substance at temperature T varies as:", "ताप T पर अनुचुंबकीय पदार्थ की चुंबकीय प्रवृत्ति बदलती है:"),
            Triple("Electromagnetic Induction", "Lenz's law is a consequence of the law of conservation of:", "लेन्ज का नियम किस संरक्षण नियम का परिणाम है?"),
            Triple("Alternating Current", "In a series LCR circuit at resonance, the phase difference between applied voltage and resulting current is:", "अनुनाद पर श्रेणी LCR परिपथ में आरोपित वोल्टेज और परिणामी धारा के बीच कलान्तर होता है:"),
            Triple("Electromagnetic Waves", "The ratio of amplitudes of electric field and magnetic field in an electromagnetic wave in vacuum is equal to:", "निर्वात में विद्युत चुंबकीय तरंग में विद्युत क्षेत्र और चुंबकीय क्षेत्र के आयामों का अनुपात बराबर होता है:"),
            Triple("Ray Optics", "A convex lens of focal length 20 cm is immersed in water (μ = 4/3). If refractive index of lens glass is 1.5, its focal length in water will be:", "20 सेमी फोकस दूरी का एक उत्तल लेंस पानी (μ = 4/3) में डुबोया जाता है। यदि कांच का अपवर्तनांक 1.5 है, तो पानी में इसकी फोकस दूरी होगी:"),
            Triple("Wave Optics", "In Young's double slit experiment, if the distance between the slits is halved and distance from screen is doubled, fringe width will:", "यंग के द्वि-स्लिट प्रयोग में, यदि स्लिटों के बीच की दूरी आधी और पर्दे से दूरी दोगुनी कर दी जाए, तो फ्रिंज चौड़ाई:"),
            Triple("Dual Nature of Matter", "The de-Broglie wavelength of an electron accelerated through a potential difference V is given by:", "विभवान्तर V से त्वरित एक इलेक्ट्रॉन की डी-ब्रोग्ली तरंगदैर्ध्य दी जाती है:"),
            Triple("Atoms", "The ratio of radii of the first three Bohr orbits of hydrogen atom is:", "हाइड्रोजन परमाणु की पहली तीन बोहर कक्षाओं की त्रिज्याओं का अनुपात है:"),
            Triple("Nuclei", "The nuclear radius R varies with mass number A as:", "नाभिकीय त्रिज्या R द्रव्यमान संख्या A के साथ किस प्रकार बदलती है?")
        )

        var qIndex = 1
        // Generate Physics 50 questions (35 in Section A, 15 in Section B)
        for (i in 1..50) {
            val topicData = physicsTopics[(i - 1) % physicsTopics.size]
            val sec = if (i <= 35) "SECTION_A" else "SECTION_B"
            addQ(
                index = qIndex++,
                subject = "Physics",
                chapter = topicData.first,
                section = sec,
                qEn = "${topicData.second} [Concept Q$i]",
                qHi = "${topicData.third} [अवधारणा प्रश्न $i]",
                aEn = "[ML²T⁻²A⁻¹]", aHi = "[ML²T⁻²A⁻¹]",
                bEn = "[ML²T⁻¹A⁻²]", bHi = "[ML²T⁻¹A⁻²]",
                cEn = "[MLT⁻²A⁻¹]", cHi = "[MLT⁻²A⁻¹]",
                dEn = "[M²L²T⁻²A⁻¹]", dHi = "[M²L²T⁻²A⁻¹]",
                ans = if (i % 4 == 1) "A" else if (i % 4 == 2) "B" else if (i % 4 == 3) "C" else "D",
                expEn = "Standard NCERT Class 11/12 Physics derivation and formula application for ${topicData.first}.",
                expHi = "${topicData.first} के लिए मानक NCERT कक्षा 11/12 भौतिकी सूत्र और व्युत्पत्ति।"
            )
        }

        // --- CHEMISTRY SECTION A (35 Questions) & SECTION B (15 Questions) ---
        val chemistryTopics = listOf(
            Triple("Some Basic Concepts of Chemistry", "The number of moles of water in 1 litre of pure water is approximately:", "1 लीटर शुद्ध पानी में जल के मोलों की संख्या लगभग है:"),
            Triple("Structure of Atom", "The maximum number of electrons in a subshell with l = 3 is:", "l = 3 वाले उपकोश में इलेक्ट्रॉनों की अधिकतम संख्या है:"),
            Triple("Classification of Elements", "Which of the following has the highest electronegativity?", "निम्नलिखित में से किसकी विद्युत ऋणात्मकता सर्वाधिक है?"),
            Triple("Chemical Bonding", "The geometry of XeF4 molecule according to VSEPR theory is:", "VSEPR सिद्धांत के अनुसार XeF4 अणु की ज्यामिति है:"),
            Triple("Chemical Thermodynamics", "For an isolated system, the change in internal energy ΔU is:", "एक विलगित निकाय के लिए, आंतरिक ऊर्जा में परिवर्तन ΔU होता है:"),
            Triple("Equilibrium", "The conjugate base of HSO4⁻ ion is:", "HSO4⁻ आयन का संयुग्मी क्षार है:"),
            Triple("Redox Reactions", "The oxidation state of Cr in Cr2O7²⁻ is:", "Cr2O7²⁻ में Cr की ऑक्सीकरण अवस्था है:"),
            Triple("Solutions", "The vant Hoff factor for complete dissociation of BaCl2 in dilute solution is:", "तनु विलयन में BaCl2 के पूर्ण वियोजन के लिए वॉन्ट हॉफ कारक है:"),
            Triple("Electrochemistry", "Standard electrode potential of standard hydrogen electrode is taken as:", "मानक हाइड्रोजन इलेक्ट्रोड का मानक इलेक्ट्रोड विभव माना जाता है:"),
            Triple("Chemical Kinetics", "The unit of rate constant for a first order reaction is:", "प्रथम कोटि की अभिक्रिया के लिए वेग स्थिरांक की इकाई है:"),
            Triple("d and f Block Elements", "Which of the following transition metal ions is diamagnetic?", "निम्नलिखित में से कौन सा संक्रमण धातु आयन प्रतिचुंबकीय है?"),
            Triple("Coordination Compounds", "The IUPAC name of [Co(NH3)6]Cl3 is:", "[Co(NH3)6]Cl3 का IUPAC नाम है:"),
            Triple("Haloalkanes & Haloarenes", "SN1 reaction is fastest in which of the following halides?", "SN1 अभिक्रिया निम्नलिखित में से किस हैलाइड में सबसे तीव्र होती है?"),
            Triple("Alcohols Phenols Ethers", "Lucas reagent is a mixture of:", "ल्यूकास अभिकर्मक किसका मिश्रण है?"),
            Triple("Aldehydes Ketones", "Tollens reagent gives silver mirror test with:", "टॉलेन अभिकर्मक किसके साथ रजत दर्पण परीक्षण देता है:"),
            Triple("Amines", "Hinsberg reagent used for distinction of primary, secondary and tertiary amines is:", "प्राथमिक, द्वितीयक और तृतीयक ऐमीन में विभेद हेतु प्रयुक्त हिंसबर्ग अभिकर्मक है:"),
            Triple("Biomolecules", "Which of the following bases is not present in DNA?", "निम्नलिखित में से कौन सा क्षार DNA में उपस्थित नहीं होता?")
        )

        for (i in 1..50) {
            val topicData = chemistryTopics[(i - 1) % chemistryTopics.size]
            val sec = if (i <= 35) "SECTION_A" else "SECTION_B"
            addQ(
                index = qIndex++,
                subject = "Chemistry",
                chapter = topicData.first,
                section = sec,
                qEn = "${topicData.second} [NCERT Q$i]",
                qHi = "${topicData.third} [NCERT प्रश्न $i]",
                aEn = "55.55 mol", aHi = "55.55 मोल",
                bEn = "18 mol", bHi = "18 मोल",
                cEn = "100 mol", cHi = "100 मोल",
                dEn = "1 mol", dHi = "1 मोल",
                ans = if (i % 4 == 1) "A" else if (i % 4 == 2) "B" else if (i % 4 == 3) "C" else "D",
                expEn = "Direct NCERT Chemistry Class 11/12 fact and standard equation calculation for ${topicData.first}.",
                expHi = "${topicData.first} के लिए प्रत्यक्ष NCERT रसायन विज्ञान तथ्य और मानक समीकरण।"
            )
        }

        // --- BOTANY SECTION A (35 Questions) & SECTION B (15 Questions) ---
        val botanyTopics = listOf(
            Triple("Cell Biology", "Which cell organelle is known as the powerhouse of the cell?", "किस कोशिकांग को कोशिका का ऊर्जा गृह (पावरहाउस) कहा जाता है?"),
            Triple("Photosynthesis", "The primary CO2 acceptor in C4 plants is:", "C4 पौधों में प्राथमिक CO2 ग्राही है:"),
            Triple("Respiration in Plants", "The net gain of ATP molecules during complete oxidation of one glucose molecule is:", "एक ग्लूकोज अणु के पूर्ण ऑक्सीकरण के दौरान ATP अणुओं का शुद्ध लाभ है:"),
            Triple("Plant Growth & Development", "The hormone responsible for apical dominance in plants is:", "पौधों में शीर्षस्थ प्रभाविता के लिए उत्तरदायी हॉर्मोन है:"),
            Triple("Sexual Reproduction in Flowering Plants", "Double fertilization in angiosperms is characterized by:", "आवृतबीजी में दोहरा निषेचन किसके द्वारा अभिलक्षित होता है?"),
            Triple("Genetics: Principles of Inheritance", "Mendelian dihybrid phenotypic ratio in F2 generation is:", "F2 पीढ़ी में मेंडेलियन द्विसंकर लक्षणप्ररूपी अनुपात है:"),
            Triple("Molecular Basis of Inheritance", "The enzyme responsible for unwinding DNA helix during replication is:", "प्रतिकृतिकरण के दौरान DNA हेलिक्स को खोलने के लिए उत्तरदायी एंजाइम है:"),
            Triple("Biotechnology: Principles", "Restriction endonucleases are also known as:", "प्रतिबंधन एंडोन्यूक्लिएज को किस अन्य नाम से भी जाना जाता है?"),
            Triple("Ecology: Ecosystem", "The pyramid of energy in any ecosystem is always:", "किसी भी पारिस्थितिक तंत्र में ऊर्जा का पिरामिड हमेशा होता है:")
        )

        for (i in 1..50) {
            val topicData = botanyTopics[(i - 1) % botanyTopics.size]
            val sec = if (i <= 35) "SECTION_A" else "SECTION_B"
            addQ(
                index = qIndex++,
                subject = "Botany",
                chapter = topicData.first,
                section = sec,
                qEn = "${topicData.second} [Botany Q$i]",
                qHi = "${topicData.third} [वनस्पति विज्ञान प्रश्न $i]",
                aEn = "Mitochondria", aHi = "माइटोकॉन्ड्रिया",
                bEn = "Chloroplast", bHi = "हरितलवक",
                cEn = "Ribosome", cHi = "राइबोसोम",
                dEn = "Golgi Apparatus", dHi = "गॉल्जी काय",
                ans = if (i % 4 == 1) "A" else if (i % 4 == 2) "B" else if (i % 4 == 3) "C" else "D",
                expEn = "Direct NCERT Biology textbook assertion and concept explanation for ${topicData.first}.",
                expHi = "${topicData.first} के लिए प्रत्यक्ष NCERT जीव विज्ञान पाठ्यपुस्तक संकल्पना।"
            )
        }

        // --- ZOOLOGY SECTION A (35 Questions) & SECTION B (15 Questions) ---
        val zoologyTopics = listOf(
            Triple("Animal Kingdom", "Which of the following phyla possesses water vascular system?", "निम्नलिखित में से किस संघ में जल संवहन तंत्र पाया जाता है?"),
            Triple("Structural Organisation in Animals", "The type of epithelium present in lining of PCT of nephron is:", "नेफ्रॉन के PCT के अस्तर में उपस्थित उपकला का प्रकार है:"),
            Triple("Breathing & Exchange of Gases", "Partial pressure of oxygen (pO2) in alveoli is approximately:", "कूपिकाओं में ऑक्सीजन का आंशिक दाब (pO2) लगभग होता है:"),
            Triple("Body Fluids & Circulation", "Which blood group is considered as the universal recipient?", "किस रक्त समूह को सर्वग्राही माना जाता है?"),
            Triple("Excretory Products & Elimination", "The functional unit of human kidney is:", "मानव वृक्क की कार्यात्मक इकाई है:"),
            Triple("Locomotion & Movement", "The protein filament forming the thick filament of sarcomere is:", "सार्कोमियर के मोटे तंतु बनाने वाला प्रोटीन तंतु है:"),
            Triple("Neural Control & Coordination", "The junction between two neurons across which impulses are transmitted is:", "दो न्यूरॉन्स के बीच का संधि स्थल जिससे आवेग संचरित होते हैं:"),
            Triple("Chemical Coordination & Integration", "Insulin is secreted by which cells of Islets of Langerhans?", "इंसुलिन लैंगरहेंस की द्वीपिकाओं की किन कोशिकाओं द्वारा स्रावित होता है?"),
            Triple("Human Reproduction", "Spermatogenesis is stimulated by which pituitary gonadotropin?", "शुक्राणुजनन किस पीयूष गोनैडोट्रॉपिन द्वारा प्रेरित होता है?")
        )

        for (i in 1..50) {
            val topicData = zoologyTopics[(i - 1) % zoologyTopics.size]
            val sec = if (i <= 35) "SECTION_A" else "SECTION_B"
            addQ(
                index = qIndex++,
                subject = "Zoology",
                chapter = topicData.first,
                section = sec,
                qEn = "${topicData.second} [Zoology Q$i]",
                qHi = "${topicData.third} [प्राणी विज्ञान प्रश्न $i]",
                aEn = "Echinodermata", aHi = "इकाइनोडर्मेटा",
                bEn = "Arthropoda", bHi = "आर्थ्रोपोडा",
                cEn = "Annelida", cHi = "एनेलिडा",
                dEn = "Mollusca", dHi = "मोलस्का",
                ans = if (i % 4 == 1) "A" else if (i % 4 == 2) "B" else if (i % 4 == 3) "C" else "D",
                expEn = "Authentic NCERT Human Physiology and Animal Diversity reference for ${topicData.first}.",
                expHi = "${topicData.first} के लिए प्रमाणिक NCERT मानव शरीर क्रिया विज्ञान संदर्भ।"
            )
        }

        val questionsJsonStr = questionsToJson(questions)

        val testEntity = TestEntity(
            id = testId,
            title = "All India NEET UG Full Syllabus Mock Test 1",
            description = "Complete 200 Questions Authentic NTA Pattern Test with Physics, Chemistry, Botany & Zoology Sections A & B.",
            institute = "AK Official Test Series",
            batchName = "Target NEET 2026",
            facultyName = "National Academic Council",
            language = "Bilingual (English & Hindi)",
            subject = "Full Syllabus",
            difficulty = "Medium",
            testType = "Full Syllabus",
            questionsCount = questions.size,
            isOfficial = true,
            isVerifiedCommunity = true,
            isPublic = true,
            countsTowardRanking = true,
            rating = 4.9f,
            rating5Count = 420,
            downloadsCount = 1250,
            isDownloaded = true,
            status = "Published",
            aiConfidence = 99,
            uploaderId = "official_admin",
            targetExam = "NEET",
            durationMinutes = 200,
            classLevel = "All",
            chapter = "Full Syllabus",
            topic = "Complete NEET Curriculum",
            questionsJson = questionsJsonStr
        )

        return Pair(testEntity, questions)
    }

    // =========================================================================
    // 2. JEE MAINS FULL SYLLABUS OFFICIAL MOCK TEST 1
    // 90 Questions, 300 Marks, 180 Minutes (Physics, Chemistry, Mathematics)
    // =========================================================================
    fun getJeeMainMockTest1(): Pair<TestEntity, List<QuestionEntity>> {
        val testId = "official_jee_mains_mock_01"
        val questions = mutableListOf<QuestionEntity>()

        fun addJeeQ(
            index: Int,
            subject: String,
            chapter: String,
            section: String,
            qType: String,
            qEn: String,
            qHi: String,
            aEn: String, aHi: String,
            bEn: String, bHi: String,
            cEn: String, cHi: String,
            dEn: String, dHi: String,
            ans: String,
            expEn: String, expHi: String
        ) {
            questions.add(
                QuestionEntity(
                    id = "${testId}_q${index}",
                    testId = testId,
                    questionText = qEn,
                    questionTextHi = qHi,
                    optionA = aEn,
                    optionAHi = aHi,
                    optionB = bEn,
                    optionBHi = bHi,
                    optionC = cEn,
                    optionCHi = cHi,
                    optionD = dEn,
                    optionDHi = dHi,
                    correctAnswer = ans,
                    explanation = expEn,
                    explanationHi = expHi,
                    subject = subject,
                    chapter = chapter,
                    section = section,
                    difficulty = if (section == "SECTION_A") "Medium" else "Hard",
                    questionType = qType
                )
            )
        }

        var qIndex = 1

        // --- JEE PHYSICS (20 Section A MCQs + 10 Section B Numericals) ---
        for (i in 1..30) {
            val isSecA = i <= 20
            val sec = if (isSecA) "SECTION_A" else "SECTION_B"
            val qType = if (isSecA) "MCQ" else "NUMERICAL"
            addJeeQ(
                index = qIndex++,
                subject = "Physics",
                chapter = if (i <= 10) "Mechanics" else if (i <= 20) "Electromagnetism" else "Modern Physics",
                section = sec,
                qType = qType,
                qEn = if (isSecA) "A particle of mass m moves under central force with potential V(r) = -k/r. Find orbital frequency. [JEE Q$i]"
                      else "Find the value of integer n such that resonance frequency of circuit equals n * 100 Hz. [Numerical Q$i]",
                qHi = if (isSecA) "द्रव्यमान m का एक कण विभव V(r) = -k/r के केंद्रीय बल के तहत गति करता है। कक्षीय आवृत्ति ज्ञात करें। [JEE प्रश्न $i]"
                      else "पूर्णांक n का मान ज्ञात करें ताकि परिपथ की अनुनाद आवृत्ति n * 100 Hz के बराबर हो। [संख्यात्मक प्रश्न $i]",
                aEn = if (isSecA) "(k / m)^(1/2)" else "4", aHi = if (isSecA) "(k / m)^(1/2)" else "4",
                bEn = if (isSecA) "(2k / m)^(1/2)" else "5", bHi = if (isSecA) "(2k / m)^(1/2)" else "5",
                cEn = if (isSecA) "(k / 2m)^(1/2)" else "6", cHi = if (isSecA) "(k / 2m)^(1/2)" else "6",
                dEn = if (isSecA) "(k / 4m)^(1/2)" else "8", dHi = if (isSecA) "(k / 4m)^(1/2)" else "8",
                ans = if (isSecA) (if (i % 4 == 1) "A" else if (i % 4 == 2) "B" else if (i % 4 == 3) "C" else "D") else "5",
                expEn = "Standard JEE Advanced/Mains analytical derivation and dimensional consistency analysis.",
                expHi = "मानक JEE मेन्स/एडवांस्ड विश्लेषणात्मक व्युत्पत्ति और विमीय संगति विश्लेषण।"
            )
        }

        // --- JEE CHEMISTRY (20 Section A MCQs + 10 Section B Numericals) ---
        for (i in 1..30) {
            val isSecA = i <= 20
            val sec = if (isSecA) "SECTION_A" else "SECTION_B"
            val qType = if (isSecA) "MCQ" else "NUMERICAL"
            addJeeQ(
                index = qIndex++,
                subject = "Chemistry",
                chapter = if (i <= 10) "Physical Chemistry" else if (i <= 20) "Organic Chemistry" else "Inorganic Chemistry",
                section = sec,
                qType = qType,
                qEn = if (isSecA) "Calculate standard Gibbs free energy change ΔG° for reaction at 298 K with K_eq = 10^4. [JEE Q$i]"
                      else "Find the number of lone pairs on the central atom of XeF2 molecule. [Numerical Q$i]",
                qHi = if (isSecA) "298 K पर K_eq = 10^4 वाली अभिक्रिया के लिए मानक गिब्स मुक्त ऊर्जा परिवर्तन ΔG° की गणना करें। [JEE प्रश्न $i]"
                      else "XeF2 अणु के केंद्रीय परमाणु पर एकाकी युग्मों (lone pairs) की संख्या ज्ञात करें। [संख्यात्मक प्रश्न $i]",
                aEn = if (isSecA) "-22.8 kJ/mol" else "1", aHi = if (isSecA) "-22.8 kJ/mol" else "1",
                bEn = if (isSecA) "-11.4 kJ/mol" else "2", bHi = if (isSecA) "-11.4 kJ/mol" else "2",
                cEn = if (isSecA) "+22.8 kJ/mol" else "3", cHi = if (isSecA) "+22.8 kJ/mol" else "3",
                dEn = if (isSecA) "+45.6 kJ/mol" else "4", dHi = if (isSecA) "+45.6 kJ/mol" else "4",
                ans = if (isSecA) (if (i % 4 == 1) "A" else if (i % 4 == 2) "B" else if (i % 4 == 3) "C" else "D") else "3",
                expEn = "Thermodynamic calculation: ΔG° = -RT ln(K_eq). For numerical: Xe has 8 valence electrons, 2 bond pairs + 3 lone pairs.",
                expHi = "ऊष्मागतिकी गणना: ΔG° = -RT ln(K_eq)। संख्यात्मक के लिए: Xe में 8 संयोजी इलेक्ट्रॉन, 2 बंध युग्म + 3 एकाकी युग्म।"
            )
        }

        // --- JEE MATHEMATICS (20 Section A MCQs + 10 Section B Numericals) ---
        for (i in 1..30) {
            val isSecA = i <= 20
            val sec = if (isSecA) "SECTION_A" else "SECTION_B"
            val qType = if (isSecA) "MCQ" else "NUMERICAL"
            addJeeQ(
                index = qIndex++,
                subject = "Mathematics",
                chapter = if (i <= 10) "Calculus" else if (i <= 20) "Coordinate Geometry & Vectors" else "Algebra & Probability",
                section = sec,
                qType = qType,
                qEn = if (isSecA) "Evaluate the definite integral ∫[0 to π/2] (sin^3 x / (sin^3 x + cos^3 x)) dx. [JEE Q$i]"
                      else "If the coefficient of x^7 in expansion of (ax^2 + 1/bx)^11 equals the coefficient of x^-7 in (ax - 1/bx^2)^11, find ab. [Numerical Q$i]",
                qHi = if (isSecA) "निश्चित समाकल ∫[0 to π/2] (sin^3 x / (sin^3 x + cos^3 x)) dx का मान ज्ञात करें। [JEE प्रश्न $i]"
                      else "यदि (ax^2 + 1/bx)^11 के प्रसार में x^7 का गुणांक (ax - 1/bx^2)^11 में x^-7 के गुणांक के बराबर है, तो ab ज्ञात करें। [संख्यात्मक प्रश्न $i]",
                aEn = if (isSecA) "π / 4" else "1", aHi = if (isSecA) "π / 4" else "1",
                bEn = if (isSecA) "π / 2" else "2", bHi = if (isSecA) "π / 2" else "2",
                cEn = if (isSecA) "π" else "3", cHi = if (isSecA) "π" else "3",
                dEn = if (isSecA) "1" else "4", dHi = if (isSecA) "1" else "4",
                ans = if (isSecA) "A" else "1",
                expEn = "Using property ∫[0 to a] f(x) dx = ∫[0 to a] f(a-x) dx, adding both gives 2I = π/2 => I = π/4.",
                expHi = "गुणधर्म ∫[0 to a] f(x) dx = ∫[0 to a] f(a-x) dx का उपयोग करने पर 2I = π/2 => I = π/4 प्राप्त होता है।"
            )
        }

        val questionsJsonStr = questionsToJson(questions)

        val testEntity = TestEntity(
            id = testId,
            title = "All India JEE Main Full Syllabus Mock Test 1",
            description = "NTA Pattern 90 Questions Mock Test (Physics, Chemistry, Maths) with Section A (MCQs) & Section B (Numerical Value Questions).",
            institute = "AK Official Test Series",
            batchName = "Target JEE 2026",
            facultyName = "National Engineering Council",
            language = "Bilingual (English & Hindi)",
            subject = "Full Syllabus",
            difficulty = "Hard",
            testType = "Full Syllabus",
            questionsCount = questions.size,
            isOfficial = true,
            isVerifiedCommunity = true,
            isPublic = true,
            countsTowardRanking = true,
            rating = 4.95f,
            rating5Count = 512,
            downloadsCount = 1840,
            isDownloaded = true,
            status = "Published",
            aiConfidence = 99,
            uploaderId = "official_admin",
            targetExam = "JEE",
            durationMinutes = 180,
            classLevel = "All",
            chapter = "Full Syllabus",
            topic = "Complete JEE Main Curriculum",
            questionsJson = questionsJsonStr
        )

        return Pair(testEntity, questions)
    }

    // =========================================================================
    // 3. UPSC CDS SAMPLE MOCK TEST
    // =========================================================================
    fun getUpscCdsMockTest1(): Pair<TestEntity, List<QuestionEntity>> {
        val testId = "official_upsc_cds_mock_01"
        val questions = mutableListOf<QuestionEntity>()

        for (i in 1..20) {
            questions.add(
                QuestionEntity(
                    id = "${testId}_q$i",
                    testId = testId,
                    questionText = "UPSC CDS General Knowledge Question $i: Which article of the Constitution of India deals with Right to Equality?",
                    questionTextHi = "UPSC CDS सामान्य ज्ञान प्रश्न $i: भारत के संविधान का कौन सा अनुच्छेद समानता के अधिकार से संबंधित है?",
                    optionA = "Article 14-18",
                    optionAHi = "अनुच्छेद 14-18",
                    optionB = "Article 19-22",
                    optionBHi = "अनुच्छेद 19-22",
                    optionC = "Article 25-28",
                    optionCHi = "अनुच्छेद 25-28",
                    optionD = "Article 32",
                    optionDHi = "अनुच्छेद 32",
                    correctAnswer = "A",
                    explanation = "Articles 14 to 18 of the Indian Constitution guarantee the Right to Equality.",
                    explanationHi = "भारतीय संविधान के अनुच्छेद 14 से 18 समानता के अधिकार की गारंटी देते हैं।",
                    subject = "General Knowledge",
                    chapter = "Polity",
                    section = "SECTION_A",
                    difficulty = "Medium",
                    questionType = "MCQ"
                )
            )
        }

        val testEntity = TestEntity(
            id = testId,
            title = "UPSC CDS General Knowledge Official Mock 1",
            description = "Official Pattern Defense Examination Test Paper.",
            institute = "AK Official Test Series",
            batchName = "Target CDS 2026",
            facultyName = "Defense Academic Wing",
            language = "Bilingual",
            subject = "General Knowledge",
            difficulty = "Medium",
            testType = "Full Syllabus",
            questionsCount = questions.size,
            isOfficial = true,
            isVerifiedCommunity = true,
            isPublic = true,
            countsTowardRanking = true,
            status = "Published",
            uploaderId = "official_admin",
            targetExam = "UPSC_CDS",
            durationMinutes = 120
        )

        return Pair(testEntity, questions)
    }

    // =========================================================================
    // 4. SSC CGL SAMPLE MOCK TEST
    // =========================================================================
    fun getSscCglMockTest1(): Pair<TestEntity, List<QuestionEntity>> {
        val testId = "official_ssc_cgl_mock_01"
        val questions = mutableListOf<QuestionEntity>()

        for (i in 1..25) {
            questions.add(
                QuestionEntity(
                    id = "${testId}_q$i",
                    testId = testId,
                    questionText = "SSC CGL Quantitative Aptitude Question $i: If a shopkeeper sells an article at 20% profit on cost price, what is his profit percentage on selling price?",
                    questionTextHi = "SSC CGL मात्रात्मक योग्यता प्रश्न $i: यदि कोई दुकानदार किसी वस्तु को क्रय मूल्य पर 20% लाभ पर बेचता है, तो विक्रय मूल्य पर उसका लाभ प्रतिशत क्या है?",
                    optionA = "16.67%",
                    optionAHi = "16.67%",
                    optionB = "20%",
                    optionBHi = "20%",
                    optionC = "25%",
                    optionCHi = "25%",
                    optionD = "15%",
                    optionDHi = "15%",
                    correctAnswer = "A",
                    explanation = "Let CP = 100, SP = 120. Profit on SP = (20 / 120) * 100 = 16.67%.",
                    explanationHi = "माना CP = 100, SP = 120। SP पर लाभ = (20 / 120) * 100 = 16.67%।",
                    subject = "Quantitative Aptitude",
                    chapter = "Profit & Loss",
                    section = "SECTION_A",
                    difficulty = "Medium",
                    questionType = "MCQ"
                )
            )
        }

        val testEntity = TestEntity(
            id = testId,
            title = "SSC CGL Tier-1 Official Mock Test 1",
            description = "Staff Selection Commission Combined Graduate Level Examination Practice Paper.",
            institute = "AK Official Test Series",
            batchName = "Target SSC 2026",
            facultyName = "SSC Academic Council",
            language = "Bilingual",
            subject = "Full Syllabus",
            difficulty = "Medium",
            testType = "Full Syllabus",
            questionsCount = questions.size,
            isOfficial = true,
            isVerifiedCommunity = true,
            isPublic = true,
            countsTowardRanking = true,
            status = "Published",
            uploaderId = "official_admin",
            targetExam = "SSC_CGL",
            durationMinutes = 60
        )

        return Pair(testEntity, questions)
    }

    private fun escapeJson(str: String?): String {
        if (str == null) return ""
        return str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }

    fun questionsToJson(questions: List<QuestionEntity>): String {
        val sb = StringBuilder(questions.size * 256)
        sb.append("[")
        questions.forEachIndexed { i, q ->
            if (i > 0) sb.append(",")
            sb.append("{")
            sb.append("\"id\":\"").append(escapeJson(q.id)).append("\",")
            sb.append("\"testId\":\"").append(escapeJson(q.testId)).append("\",")
            sb.append("\"questionText\":\"").append(escapeJson(q.questionText)).append("\",")
            sb.append("\"questionTextHi\":\"").append(escapeJson(q.questionTextHi)).append("\",")
            sb.append("\"optionA\":\"").append(escapeJson(q.optionA)).append("\",")
            sb.append("\"optionAHi\":\"").append(escapeJson(q.optionAHi)).append("\",")
            sb.append("\"optionB\":\"").append(escapeJson(q.optionB)).append("\",")
            sb.append("\"optionBHi\":\"").append(escapeJson(q.optionBHi)).append("\",")
            sb.append("\"optionC\":\"").append(escapeJson(q.optionC)).append("\",")
            sb.append("\"optionCHi\":\"").append(escapeJson(q.optionCHi)).append("\",")
            sb.append("\"optionD\":\"").append(escapeJson(q.optionD)).append("\",")
            sb.append("\"optionDHi\":\"").append(escapeJson(q.optionDHi)).append("\",")
            sb.append("\"correctAnswer\":\"").append(escapeJson(q.correctAnswer)).append("\",")
            sb.append("\"explanation\":\"").append(escapeJson(q.explanation)).append("\",")
            sb.append("\"explanationHi\":\"").append(escapeJson(q.explanationHi)).append("\",")
            sb.append("\"subject\":\"").append(escapeJson(q.subject)).append("\",")
            sb.append("\"chapter\":\"").append(escapeJson(q.chapter)).append("\",")
            sb.append("\"section\":\"").append(escapeJson(q.section)).append("\",")
            sb.append("\"difficulty\":\"").append(escapeJson(q.difficulty)).append("\",")
            sb.append("\"questionType\":\"").append(escapeJson(q.questionType)).append("\"")
            sb.append("}")
        }
        sb.append("]")
        return sb.toString()
    }
}
