-- Scripts de vérification post-migration
-- À exécuter après la migration pour valider l'intégrité des données

-- ========================================
-- 1. Vérifier que toutes les rooms ont m.space.parent
-- ========================================

SELECT 
    r.room_id,
    r.creator,
    CASE 
        WHEN se.event_id IS NOT NULL THEN 'HAS m.space.parent'
        ELSE 'MISSING m.space.parent'
    END as space_parent_status
FROM public.rooms r
LEFT JOIN public.state_events se ON se.room_id = r.room_id 
    AND se.type = 'm.space.parent'
WHERE r.room_id LIKE '%:chat.neohoods.com'
ORDER BY space_parent_status, r.room_id;

-- ========================================
-- 2. Compter les rooms migrées par statut
-- ========================================

SELECT 
    COUNT(*) as total_rooms,
    COUNT(CASE WHEN se.event_id IS NOT NULL THEN 1 END) as rooms_with_space_parent,
    COUNT(CASE WHEN se.event_id IS NULL THEN 1 END) as rooms_without_space_parent
FROM public.rooms r
LEFT JOIN public.state_events se ON se.room_id = r.room_id 
    AND se.type = 'm.space.parent'
WHERE r.room_id LIKE '%:chat.neohoods.com';

-- ========================================
-- 3. Vérifier les state_events par room
-- ========================================

SELECT 
    r.room_id,
    COUNT(se.event_id) as state_event_count,
    COUNT(CASE WHEN se.type = 'm.room.name' THEN 1 END) as has_name,
    COUNT(CASE WHEN se.type = 'm.space.parent' THEN 1 END) as has_space_parent,
    COUNT(CASE WHEN se.type = 'm.room.encryption' THEN 1 END) as has_encryption
FROM public.rooms r
LEFT JOIN public.state_events se ON se.room_id = r.room_id
WHERE r.room_id LIKE '%:chat.neohoods.com'
GROUP BY r.room_id
ORDER BY r.room_id;

-- ========================================
-- 4. Vérifier les events (messages) par room
-- ========================================

SELECT 
    r.room_id,
    COUNT(e.event_id) as message_count,
    MIN(e.origin_server_ts) as first_message_ts,
    MAX(e.origin_server_ts) as last_message_ts
FROM public.rooms r
LEFT JOIN public.events e ON e.room_id = r.room_id 
    AND e.type = 'm.room.message'
WHERE r.room_id LIKE '%:chat.neohoods.com'
GROUP BY r.room_id
ORDER BY r.room_id;

-- ========================================
-- 5. Vérifier les users (senders) dans les events
-- ========================================

SELECT 
    e.sender,
    COUNT(*) as message_count,
    COUNT(DISTINCT e.room_id) as rooms_count
FROM public.events e
WHERE e.type = 'm.room.message'
    AND e.room_id LIKE '%:chat.neohoods.com'
GROUP BY e.sender
ORDER BY message_count DESC;

-- ========================================
-- 6. Vérifier l'intégrité des références (prev_state)
-- ========================================

SELECT 
    se.room_id,
    se.event_id,
    se.type,
    se.prev_state,
    CASE 
        WHEN se.prev_state IS NULL THEN 'OK (no prev_state)'
        WHEN se2.event_id IS NOT NULL THEN 'OK (prev_state exists)'
        ELSE 'ERROR (prev_state missing)'
    END as prev_state_status
FROM public.state_events se
LEFT JOIN public.state_events se2 ON se2.event_id = se.prev_state
WHERE se.room_id LIKE '%:chat.neohoods.com'
    AND se.prev_state IS NOT NULL
ORDER BY se.room_id, se.event_id;

-- ========================================
-- 7. Vérifier les rooms réutilisées vs nouvelles
-- ========================================

-- Cette requête nécessite de connaître quelles rooms ont été réutilisées
-- À adapter selon votre mapping

SELECT 
    r.room_id,
    r.creator,
    r.room_version,
    COUNT(DISTINCT e.event_id) as total_events,
    COUNT(DISTINCT CASE WHEN e.type = 'm.room.message' THEN e.event_id END) as message_events
FROM public.rooms r
LEFT JOIN public.events e ON e.room_id = r.room_id
WHERE r.room_id LIKE '%:chat.neohoods.com'
GROUP BY r.room_id, r.creator, r.room_version
ORDER BY r.room_id;

-- ========================================
-- 8. Vérifier les event IDs uniques
-- ========================================

SELECT 
    event_id,
    COUNT(*) as duplicate_count
FROM public.events
WHERE room_id LIKE '%:chat.neohoods.com'
GROUP BY event_id
HAVING COUNT(*) > 1;

-- ========================================
-- 9. Statistiques globales
-- ========================================

SELECT 
    'Total rooms' as metric,
    COUNT(*)::text as value
FROM public.rooms
WHERE room_id LIKE '%:chat.neohoods.com'

UNION ALL

SELECT 
    'Rooms with m.space.parent',
    COUNT(DISTINCT se.room_id)::text
FROM public.state_events se
WHERE se.type = 'm.space.parent'
    AND se.room_id LIKE '%:chat.neohoods.com'

UNION ALL

SELECT 
    'Total state events',
    COUNT(*)::text
FROM public.state_events
WHERE room_id LIKE '%:chat.neohoods.com'

UNION ALL

SELECT 
    'Total message events',
    COUNT(*)::text
FROM public.events
WHERE type = 'm.room.message'
    AND room_id LIKE '%:chat.neohoods.com'

UNION ALL

SELECT 
    'Unique users (senders)',
    COUNT(DISTINCT sender)::text
FROM public.events
WHERE type = 'm.room.message'
    AND room_id LIKE '%:chat.neohoods.com';

