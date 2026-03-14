-- Align activity types with canonical set: codes PROJ, PROD, WEB3, INNO and coherent TJM.
UPDATE activity_types SET label = 'Développement', daily_rate_cents = 60000, description = 'Conception, développement, test, documentation, ingénierie logicielle, déploiement et packaging. Synonymes: dev, dev logiciel.' WHERE code = 'DEV';
UPDATE activity_types SET label = 'Expertise IA', daily_rate_cents = 100000, description = 'Prompt engineering, conception d''agents IA, architecture IA (LLM, RAG, MCP).' WHERE code = 'AI';
UPDATE activity_types SET label = 'Marketing', daily_rate_cents = 70000, description = 'Gestion de communauté, production de contenu, médias sociaux, SEO.' WHERE code = 'MARK';

MERGE INTO activity_types (code, label, daily_rate_cents, description) KEY(code) VALUES
    ('PROJ', 'Gestion de projet', 30000, 'Rendez-vous, coordination, administratif, suivi. PM, coordination projet.'),
    ('PROD', 'Gestion de produit logiciel', 50000, 'Coordination des évolutions logicielles, priorisation des besoins, suivi des développements.'),
    ('PROSPECT', 'Prospection commerciale', 50000, 'Recherche de prospects, suivi des leads, négociations initiales.'),
    ('WEB3', 'Expertise blockchain', 100000, 'Conception et déploiement de contrats intelligents, R&D blockchain.'),
    ('INNO', 'Conseil en innovation', 70000, 'Gestion de projet R&D, études état de l''art, articles et dossiers d''innovation.');
